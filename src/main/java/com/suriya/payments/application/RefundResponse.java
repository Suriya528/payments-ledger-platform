package com.suriya.payments.application;

import com.suriya.payments.domain.RefundStatus;
import java.time.Instant;
import java.util.UUID;

/** Public view of a refund. */
public record RefundResponse(
    UUID id, UUID paymentId, long amount, RefundStatus status, Instant createdAt) {}
