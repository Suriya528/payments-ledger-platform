package com.suriya.payments.application;

import com.suriya.payments.domain.Account;
import com.suriya.payments.domain.AccountBalance;
import com.suriya.payments.domain.AccountType;
import com.suriya.payments.domain.Direction;
import com.suriya.payments.domain.LedgerService;
import com.suriya.payments.domain.LedgerService.Posting;
import com.suriya.payments.domain.OwnerType;
import com.suriya.payments.domain.Payment;
import com.suriya.payments.domain.PaymentStatus;
import com.suriya.payments.domain.Refund;
import com.suriya.payments.domain.RefundStatus;
import com.suriya.payments.domain.TransactionType;
import com.suriya.payments.repository.AccountBalanceRepository;
import com.suriya.payments.repository.AccountRepository;
import com.suriya.payments.repository.PaymentRepository;
import com.suriya.payments.repository.RefundRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The hold -> settle/reverse saga. Each phase is its own transaction so the (mock) PSP call sits
 * between a committed hold and the settle/reverse — never holding a DB lock across the provider
 * call.
 */
@Service
public class PaymentSaga {

  private final PaymentRepository payments;
  private final AccountRepository accounts;
  private final LedgerService ledger;
  private final OutboxWriter outboxWriter;
  private final RefundRepository refunds;

  @Value("${payments.fee-basis-points:0}")
  private int feeBasisPoints;

  public PaymentSaga(
      PaymentRepository payments,
      AccountRepository accounts,
      LedgerService ledger,
      OutboxWriter outboxWriter,
      RefundRepository refunds) {
    this.payments = payments;
    this.accounts = accounts;
    this.ledger = ledger;
    this.outboxWriter = outboxWriter;
    this.refunds = refunds;
  }

  /** Tx1: persist the payment (PROCESSING) and move payer -> suspense. */
  @Transactional
  public Payment hold(UUID paymentId, CreatePaymentRequest request) {
    Payment payment = new Payment();
    payment.setId(paymentId);
    payment.setMerchantId(request.merchantId());
    payment.setPayerAccount(request.payerAccountId());
    payment.setPayeeAccount(request.payeeAccountId());
    payment.setAmount(request.amount());
    payment.setCurrency(request.currency().toUpperCase());
    payment.setStatus(PaymentStatus.PROCESSING);
    payment.setCreatedAt(Instant.now());
    payment.setUpdatedAt(Instant.now());
    payments.save(payment);

    ledger.post(
        TransactionType.PAYMENT,
        paymentId,
        List.of(
            new Posting(request.payerAccountId(), Direction.DEBIT, request.amount()),
            new Posting(suspenseAccountId(), Direction.CREDIT, request.amount())));
    return payment;
  }

  /** Tx2 (approved): move suspense -> payee (and fee -> fee_income) and mark payment succeeded. */
  @Transactional
  public void settle(UUID paymentId, String pspReference) {
    Payment payment = payments.findById(paymentId).orElseThrow();
    long feeAmount = (payment.getAmount() * feeBasisPoints) / 10_000L;
    long payeeAmount = payment.getAmount() - feeAmount;

    List<Posting> postings = new ArrayList<>();
    postings.add(new Posting(suspenseAccountId(), Direction.DEBIT, payment.getAmount()));
    postings.add(new Posting(payment.getPayeeAccount(), Direction.CREDIT, payeeAmount));
    if (feeAmount > 0) {
      postings.add(new Posting(feeIncomeAccountId(), Direction.CREDIT, feeAmount));
    }
    ledger.post(TransactionType.PAYMENT, paymentId, postings);

    payment.setStatus(PaymentStatus.SUCCEEDED);
    payment.setPspReference(pspReference);
    payment.setUpdatedAt(Instant.now());
    outboxWriter.write(PaymentEventTypes.SUCCEEDED, toEvent(payment));
  }

  /** Tx2 (declined): return suspense -> payer and mark the payment failed. */
  @Transactional
  public void reverse(UUID paymentId) {
    Payment payment = payments.findById(paymentId).orElseThrow();
    ledger.post(
        TransactionType.REVERSAL,
        paymentId,
        List.of(
            new Posting(suspenseAccountId(), Direction.DEBIT, payment.getAmount()),
            new Posting(payment.getPayerAccount(), Direction.CREDIT, payment.getAmount())));
    payment.setStatus(PaymentStatus.FAILED);
    payment.setUpdatedAt(Instant.now());
    outboxWriter.write(PaymentEventTypes.FAILED, toEvent(payment));
  }

  /**
   * PSP authorized but capture is asynchronous: mark the held payment AUTHORIZED, await a webhook.
   */
  @Transactional
  public void authorizePending(UUID paymentId, String pspReference) {
    Payment payment = payments.findById(paymentId).orElseThrow();
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setPspReference(pspReference);
    payment.setUpdatedAt(Instant.now());
  }

  /**
   * PSP call timed out or failed with network ambiguity: hold is preserved, marked for background reconciliation.
   */
  @Transactional
  public void markRequiresReconciliation(UUID paymentId) {
    Payment payment = payments.findById(paymentId).orElseThrow();
    payment.setStatus(PaymentStatus.REQUIRES_RECONCILIATION);
    payment.setUpdatedAt(Instant.now());
  }

  /**
   * Refund (full or partial) a captured payment: move payee (and fee) -> payer and update the running total.
   */
  @Transactional
  public void refund(UUID refundId, UUID paymentId, long amount) {
    Payment payment =
        payments.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    if (payment.getStatus() != PaymentStatus.SUCCEEDED
        && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
      throw new RefundNotAllowedException(
          "payment %s is not refundable in status %s".formatted(paymentId, payment.getStatus()));
    }
    long remaining = payment.getAmount() - payment.getRefundedAmount();
    if (amount > remaining) {
      throw new RefundNotAllowedException(
          "refund %d exceeds the remaining refundable amount %d".formatted(amount, remaining));
    }

    long feeRefund = (amount * feeBasisPoints) / 10_000L;
    long payeeRefund = amount - feeRefund;

    List<Posting> postings = new ArrayList<>();
    postings.add(new Posting(payment.getPayeeAccount(), Direction.DEBIT, payeeRefund));
    if (feeRefund > 0) {
      postings.add(new Posting(feeIncomeAccountId(), Direction.DEBIT, feeRefund));
    }
    postings.add(new Posting(payment.getPayerAccount(), Direction.CREDIT, amount));
    ledger.post(TransactionType.REFUND, paymentId, postings);

    Refund refund = new Refund();
    refund.setId(refundId);
    refund.setPaymentId(paymentId);
    refund.setAmount(amount);
    refund.setStatus(RefundStatus.SUCCEEDED);
    refund.setCreatedAt(Instant.now());
    refunds.save(refund);

    long refundedTotal = payment.getRefundedAmount() + amount;
    payment.setRefundedAmount(refundedTotal);
    payment.setStatus(
        refundedTotal == payment.getAmount()
            ? PaymentStatus.REFUNDED
            : PaymentStatus.PARTIALLY_REFUNDED);
    payment.setUpdatedAt(Instant.now());
    outboxWriter.write(PaymentEventTypes.REFUNDED, toEvent(payment));
  }

  private static PaymentEvent toEvent(Payment payment) {
    return new PaymentEvent(
        UUID.randomUUID(),
        payment.getId(),
        payment.getMerchantId(),
        payment.getStatus(),
        payment.getAmount(),
        payment.getCurrency(),
        Instant.now());
  }

  private UUID suspenseAccountId() {
    Account suspense =
        accounts
            .findFirstByType(AccountType.PSP_SUSPENSE)
            .orElseThrow(() -> new IllegalStateException("no PSP_SUSPENSE account configured"));
    return suspense.getId();
  }

  private UUID feeIncomeAccountId() {
    Account feeAccount =
        accounts
            .findFirstByType(AccountType.FEE_INCOME)
            .orElseThrow(() -> new IllegalStateException("no FEE_INCOME account configured"));
    return feeAccount.getId();
  }
}
