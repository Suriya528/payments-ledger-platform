package com.suriya.payments.application;

import com.suriya.payments.domain.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

/** Public view of a payment. */
public record PaymentResponse(
    UUID id, PaymentStatus status, long amount, String currency, Instant createdAt) {}
