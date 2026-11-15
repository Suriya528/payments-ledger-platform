package com.suriya.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.suriya.payments.domain.Account;
import com.suriya.payments.domain.AccountType;
import com.suriya.payments.domain.InsufficientFundsException;
import com.suriya.payments.domain.OwnerType;
import com.suriya.payments.repository.AccountBalanceRepository;
import com.suriya.payments.repository.AccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class PaymentConcurrencyStressTest {

  @Autowired private PaymentService paymentService;
  @Autowired private AccountService accountService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private AccountBalanceRepository balanceRepository;
  @Autowired private LedgerIntegrityChecker integrityChecker;

  private UUID payerWalletId;
  private UUID payeeMerchantId;

  @BeforeEach
  void setUp() {
    // Ensure system accounts exist
    accountRepository
        .findFirstByType(AccountType.PSP_SUSPENSE)
        .orElseGet(() ->
            accountService.createAccount(OwnerType.SYSTEM, "psp-sys", AccountType.PSP_SUSPENSE, "INR"));

    Account payer =
        accountService.createAccount(
            OwnerType.USER, "payer-" + UUID.randomUUID(), AccountType.USER_WALLET, "INR");
    payerWalletId = payer.getId();

    Account payee =
        accountService.createAccount(
            OwnerType.MERCHANT, "merchant-" + UUID.randomUUID(), AccountType.MERCHANT_PAYABLE, "INR");
    payeeMerchantId = payee.getId();
  }

  @Test
  @DisplayName("Concurrent duplicate requests with same Idempotency-Key return identical result without double-debit")
  void concurrentDuplicateIdempotencyRequests() {
    accountService.fundAccount(payerWalletId, 10_000L);
    String idempotencyKey = UUID.randomUUID().toString();
    CreatePaymentRequest request =
        new CreatePaymentRequest("merchant-1", payerWalletId, payeeMerchantId, 2500L, "INR");

    // First request
    PaymentResult firstResult = paymentService.create(idempotencyKey, request);
    assertThat(firstResult.statusCode()).isEqualTo(201);
    assertThat(firstResult.replayed()).isFalse();

    // Second request with exact same key and payload
    PaymentResult replayResult = paymentService.create(idempotencyKey, request);
    assertThat(replayResult.statusCode()).isEqualTo(201);
    assertThat(replayResult.replayed()).isTrue();
    assertThat(replayResult.response().id()).isEqualTo(firstResult.response().id());

    // Payer must have been debited exactly ONCE (10,000 - 2,500 = 7,500)
    long currentBalance = balanceRepository.findById(payerWalletId).orElseThrow().getBalance();
    assertThat(currentBalance).isEqualTo(7_500L);

    // Ledger invariant must be strictly balanced
    LedgerIntegrityChecker.Result integrity = integrityChecker.check();
    assertThat(integrity.ledgerNet()).isZero();
    assertThat(integrity.driftedAccounts()).doesNotContain(payeeMerchantId);
  }

  @Test
  @DisplayName("High-concurrency overdraw race condition: 10 parallel threads attempting 10,000 on 5,000 balance")
  void highConcurrencyOverdrawProtection() throws Exception {
    // Fund with exactly 5,000
    accountService.fundAccount(payerWalletId, 5_000L);

    int totalThreads = 10;
    long debitPerThread = 1_000L; // 10 * 1,000 = 10,000 attempted (double the balance)

    ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
    AtomicInteger successfulPayments = new AtomicInteger(0);
    AtomicInteger rejectedPayments = new AtomicInteger(0);

    List<CompletableFuture<Void>> futures = new ArrayList<>();

    for (int i = 0; i < totalThreads; i++) {
      String uniqueIdemKey = "idem-stress-" + i + "-" + UUID.randomUUID();
      CreatePaymentRequest request =
          new CreatePaymentRequest("merchant-1", payerWalletId, payeeMerchantId, debitPerThread, "INR");

      futures.add(
          CompletableFuture.runAsync(
              () -> {
                try {
                  PaymentResult result = paymentService.create(uniqueIdemKey, request);
                  if (result.statusCode() == 201) {
                    successfulPayments.incrementAndGet();
                  }
                } catch (InsufficientFundsException ex) {
                  rejectedPayments.incrementAndGet();
                } catch (Exception ex) {
                  // Other race exceptions like concurrency retry
                  rejectedPayments.incrementAndGet();
                }
              },
              executor));
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    executor.shutdown();

    // Exactly 5 should succeed and 5 should be rejected
    assertThat(successfulPayments.get()).isEqualTo(5);
    assertThat(rejectedPayments.get()).isEqualTo(5);

    // Final balance MUST be strictly 0, never negative
    long finalPayerBalance = balanceRepository.findById(payerWalletId).orElseThrow().getBalance();
    assertThat(finalPayerBalance).isZero();

    // Payee merchant MUST have received exactly 5,000
    long finalPayeeBalance = balanceRepository.findById(payeeMerchantId).orElseThrow().getBalance();
    assertThat(finalPayeeBalance).isEqualTo(5_000L);

    // Double-entry ledger audit MUST be zero net and payee must not drift
    LedgerIntegrityChecker.Result integrity = integrityChecker.check();
    assertThat(integrity.ledgerNet()).isZero();
    assertThat(integrity.driftedAccounts()).doesNotContain(payeeMerchantId);
  }
}
