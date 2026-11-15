package com.suriya.payments.infrastructure.psp;

import com.suriya.payments.domain.Payment;

/** Abstraction over an external payment provider, so the saga is provider-agnostic. */
public interface PaymentProvider {

  AuthorizationResult authorize(Payment payment);
}
