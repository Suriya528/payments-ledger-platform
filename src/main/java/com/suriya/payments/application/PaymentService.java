package com.suriya.payments.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suriya.payments.domain.Account;
import com.suriya.payments.domain.AccountType;
import com.suriya.payments.domain.IdempotencyRecord;
import com.suriya.payments.domain.IdempotencyStatus;
import com.suriya.payments.domain.Payment;
import com.suriya.payments.infrastructure.psp.AuthorizationResult;
import com.suriya.payments.infrastructure.psp.PaymentProvider;
import com.suriya.payments.infrastructure.security.MerchantPrincipal;
import com.suriya.payments.repository.AccountRepository;
import com.suriya.payments.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Orchestrates payment creation: validation, idempotency claim/replay, hold -> PSP -> settle/reverse saga,
 * and resilient failure recovery on gateway timeouts.
 */
@Service
public class PaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
  private static final int CREATED = 201;
  private static final int ACCEPTED = 202;

  private final IdempotencyService idempotency;
  private final PaymentSaga saga;
  private final PaymentProvider psp;
  private final PaymentRepository payments;
  private final AccountRepository accounts;
  private final ObjectMapper json;
  private final MeterRegistry metrics;

  public PaymentService(
      IdempotencyService idempotency,
      PaymentSaga saga,
      PaymentProvider psp,
      PaymentRepository payments,
      AccountRepository accounts,
      ObjectMapper json,
      MeterRegistry metrics) {
    this.idempotency = idempotency;
    this.saga = saga;
    this.psp = psp;
    this.payments = payments;
    this.accounts = accounts;
    this.json = json;
    this.metrics = metrics;
  }

  public PaymentResult create(String idempotencyKey, CreatePaymentRequest request) {
    // 1. Strict financial validation before modifying ledger or idempotency state
    validateTransaction(request);

    String requestHash = RequestHasher.sha256(canonicalize(request));

    // 2. Check existing idempotency claim
    var alreadySeen = idempotency.find(request.merchantId(), idempotencyKey);
    if (alreadySeen.isPresent()) {
      return replayOrReject(alreadySeen.get(), idempotencyKey, requestHash);
    }

    IdempotencyRecord claim;
    try {
      claim = idempotency.open(request.merchantId(), idempotencyKey, requestHash);
    } catch (DataIntegrityViolationException raced) {
      IdempotencyRecord winner =
          idempotency
              .find(request.merchantId(), idempotencyKey)
              .orElseThrow(
                  () -> new IllegalStateException("idempotency record vanished after conflict"));
      return replayOrReject(winner, idempotencyKey, requestHash);
    }

    // 3. Execute hold -> PSP -> settle saga with failure recovery
    UUID paymentId = UUID.randomUUID();
    int responseCode = CREATED;
    try {
      Payment payment = saga.hold(paymentId, request);
      idempotency.attachResource(claim.getId(), paymentId);

      try {
        AuthorizationResult authorization = psp.authorize(payment);
        switch (authorization.outcome()) {
          case CAPTURED -> saga.settle(paymentId, authorization.reference());
          case PENDING -> saga.authorizePending(paymentId, authorization.reference());
          case DECLINED -> saga.reverse(paymentId);
        }
      } catch (RuntimeException pspEx) {
        log.warn(
            "PSP authorization failed for held payment {}: {}. Preserving hold for reconciliation.",
            paymentId,
            pspEx.getMessage());
        saga.markRequiresReconciliation(paymentId);
        responseCode = ACCEPTED;
      }
    } catch (RuntimeException holdFailure) {
      // Hold failed (e.g. InsufficientFundsException) BEFORE funds moved. Safe to release claim.
      idempotency.release(claim.getId());
      throw holdFailure;
    }

    Payment finalState = load(paymentId);
    PaymentResponse response = toResponse(finalState);
    String responseJson = null;
    try {
      responseJson = json.writeValueAsString(response);
    } catch (Exception ignored) {
    }

    idempotency.markCompleted(claim.getId(), paymentId, responseCode, responseJson);
    metrics.counter("payments.created", "outcome", finalState.getStatus().name()).increment();
    return new PaymentResult(response, responseCode, false);
  }

  public PaymentResponse get(UUID paymentId) {
    Payment payment = load(paymentId);
    validateReadOwnership(payment);
    return toResponse(payment);
  }

  private void validateTransaction(CreatePaymentRequest request) {
    if (request.amount() <= 0) {
      throw new InvalidFinancialTransactionException(
          "Payment amount must be strictly positive: " + request.amount());
    }
    if (request.payerAccountId().equals(request.payeeAccountId())) {
      throw new InvalidFinancialTransactionException(
          "Payer and payee accounts cannot be the same: " + request.payerAccountId());
    }

    Account payer =
        accounts
            .findById(request.payerAccountId())
            .orElseThrow(() -> new AccountNotFoundException(request.payerAccountId()));
    Account payee =
        accounts
            .findById(request.payeeAccountId())
            .orElseThrow(() -> new AccountNotFoundException(request.payeeAccountId()));

    if (payer.getType() != AccountType.USER_WALLET) {
      throw new InvalidFinancialTransactionException(
          "Payer account must be USER_WALLET, found: " + payer.getType());
    }
    if (payee.getType() != AccountType.MERCHANT_PAYABLE) {
      throw new InvalidFinancialTransactionException(
          "Payee account must be MERCHANT_PAYABLE, found: " + payee.getType());
    }
    if (!payer.getCurrency().equalsIgnoreCase(request.currency())) {
      throw new InvalidFinancialTransactionException(
          "Payer currency %s does not match payment currency %s"
              .formatted(payer.getCurrency(), request.currency()));
    }
    if (!payee.getCurrency().equalsIgnoreCase(request.currency())) {
      throw new InvalidFinancialTransactionException(
          "Payee currency %s does not match payment currency %s"
              .formatted(payee.getCurrency(), request.currency()));
    }

    // Tenant / Merchant isolation check
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof MerchantPrincipal mp) {
      if (!mp.merchantId().equals(request.merchantId())) {
        throw new AccessDeniedException(
            "Authenticated merchant %s is not permitted to create payments for merchant %s"
                .formatted(mp.merchantId(), request.merchantId()));
      }
      if (!payee.getOwnerId().equals(mp.merchantId())) {
        throw new AccessDeniedException(
            "Merchant %s does not own the destination account %s"
                .formatted(mp.merchantId(), payee.getId()));
      }
    }
  }

  private void validateReadOwnership(Payment payment) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof MerchantPrincipal mp) {
      if (!mp.merchantId().equals(payment.getMerchantId())) {
        throw new AccessDeniedException(
            "Merchant %s is not authorized to access payment %s"
                .formatted(mp.merchantId(), payment.getId()));
      }
    }
  }

  private PaymentResult replayOrReject(IdempotencyRecord record, String key, String requestHash) {
    if (!record.getRequestHash().equals(requestHash)) {
      throw new IdempotencyMismatchException(key);
    }
    if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
      throw new IdempotencyConflictException(key);
    }
    if (record.getResponseBody() != null) {
      try {
        PaymentResponse cached = json.readValue(record.getResponseBody(), PaymentResponse.class);
        return new PaymentResult(cached, record.getResponseCode(), true);
      } catch (Exception ignored) {
      }
    }
    return new PaymentResult(
        toResponse(load(record.getResourceId())), record.getResponseCode(), true);
  }

  private Payment load(UUID paymentId) {
    return payments.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
  }

  private static String canonicalize(CreatePaymentRequest r) {
    return String.join(
        "|",
        r.merchantId(),
        String.valueOf(r.payerAccountId()),
        String.valueOf(r.payeeAccountId()),
        String.valueOf(r.amount()),
        r.currency());
  }

  private static PaymentResponse toResponse(Payment p) {
    return new PaymentResponse(
        p.getId(), p.getStatus(), p.getAmount(), p.getCurrency(), p.getCreatedAt());
  }
}
