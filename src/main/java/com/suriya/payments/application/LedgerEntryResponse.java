package com.suriya.payments.application;

import com.suriya.payments.domain.Direction;
import java.time.Instant;
import java.util.UUID;

/** A single append-only posting on an account. */
public record LedgerEntryResponse(
    UUID transactionId, Direction direction, long amount, Instant createdAt) {}
