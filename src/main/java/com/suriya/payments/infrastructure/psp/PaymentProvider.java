package com.suriya.payments.infrastructure.psp;

import com.suriya.payments.domain.Payment;
import java.util.Optional;
import java.util.UUID;

/** Abstraction over an external payment provider, so the saga is provider-agnostic. */
public interface PaymentProvider {

  AuthorizationResult authorize(Payment payment);

  /**
   * Queries the provider for the status of an in-flight or timed-out payment.
   * Used by the reconciliation engine to resolve uncertain payment outcomes safely.
   */
  default Optional<AuthorizationResult> queryStatus(UUID paymentId) {
    return Optional.empty();
  }
}
