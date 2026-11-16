package com.suriya.payments.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LedgerAuditReport(
    AuditStatus status,
    long netVariance,
    int driftedAccountsCount,
    List<UUID> driftedAccounts,
    long totalTransactions,
    long totalPostings,
    String solvencyFingerprint,
    Instant auditedAt) {

  public enum AuditStatus {
    VERIFIED_HEALTHY,
    IMBALANCE_DETECTED
  }
}
