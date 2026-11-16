package com.suriya.payments.application;

import com.suriya.payments.domain.Payment;
import com.suriya.payments.domain.PaymentStatus;
import com.suriya.payments.infrastructure.psp.AuthorizationOutcome;
import com.suriya.payments.infrastructure.psp.AuthorizationResult;
import com.suriya.payments.infrastructure.psp.PaymentProvider;
import com.suriya.payments.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Continuous safety net:
 * 1. Asserts ledger balance integrity (debits = credits, balance = sum(postings)).
 * 2. Reconciles uncertain in-flight payments (PROCESSING, REQUIRES_RECONCILIATION, AUTHORIZED)
 *    by verifying outcome with payment provider before settling or reversing back to payer.
 */
@Service
public class ReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

  private final LedgerIntegrityChecker integrity;
  private final PaymentRepository payments;
  private final PaymentSaga saga;
  private final PaymentProvider psp;
  private final Duration authorizedTimeout;
  private final AtomicLong ledgerImbalance = new AtomicLong(0);

  public ReconciliationService(
      LedgerIntegrityChecker integrity,
      PaymentRepository payments,
      PaymentSaga saga,
      PaymentProvider psp,
      MeterRegistry registry,
      @Value("${reconciliation.authorized-timeout:30m}") Duration authorizedTimeout) {
    this.integrity = integrity;
    this.payments = payments;
    this.saga = saga;
    this.psp = psp;
    this.authorizedTimeout = authorizedTimeout;
    registry.gauge("ledger.imbalance", ledgerImbalance);
  }

  @Scheduled(fixedDelayString = "${reconciliation.delay-ms:60000}")
  public void scheduledRun() {
    reconcile();
  }

  public ReconciliationReport reconcile() {
    LedgerIntegrityChecker.Result result = integrity.check();
    ledgerImbalance.set(result.ledgerNet());
    if (!result.balanced()) {
      log.error(
          "LEDGER IMBALANCE detected: net={} driftedAccounts={}",
          result.ledgerNet(),
          result.driftedAccounts());
    }
    int recovered = reconcileStalePayments();
    return new ReconciliationReport(result.ledgerNet(), result.driftedAccounts(), recovered);
  }

  private int reconcileStalePayments() {
    Instant cutoff = Instant.now().minus(authorizedTimeout);
    List<Payment> staleAuthorized =
        payments.findByStatusAndUpdatedAtBefore(PaymentStatus.AUTHORIZED, cutoff);
    List<Payment> staleProcessing =
        payments.findByStatusAndUpdatedAtBefore(PaymentStatus.PROCESSING, cutoff);
    List<Payment> staleReconcile =
        payments.findByStatusAndUpdatedAtBefore(PaymentStatus.REQUIRES_RECONCILIATION, cutoff);

    List<Payment> allStale = new ArrayList<>();
    allStale.addAll(staleAuthorized);
    allStale.addAll(staleProcessing);
    allStale.addAll(staleReconcile);

    for (Payment payment : allStale) {
      log.info("Reconciling stale payment {} in status {}", payment.getId(), payment.getStatus());
      try {
        Optional<AuthorizationResult> providerStatus = psp.queryStatus(payment.getId());
        if (providerStatus.isPresent()
            && providerStatus.get().outcome() == AuthorizationOutcome.CAPTURED) {
          log.info(
              "Provider confirmed payment {} was CAPTURED. Settling now.", payment.getId());
          saga.settle(payment.getId(), providerStatus.get().reference());
        } else {
          log.warn(
              "Provider did not capture stale payment {} (or hold expired). Reversing funds to payer.",
              payment.getId());
          saga.reverse(payment.getId());
        }
      } catch (Exception e) {
        log.error("Failed to reconcile payment {} with provider. Will retry next cycle.", payment.getId(), e);
      }
    }
    return allStale.size();
  }
}
