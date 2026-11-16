package com.suriya.payments.application;

import com.suriya.payments.repository.LedgerEntryRepository;
import com.suriya.payments.repository.LedgerTransactionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerAuditService {

  private final LedgerIntegrityChecker integrityChecker;
  private final LedgerTransactionRepository transactions;
  private final LedgerEntryRepository entries;

  public LedgerAuditService(
      LedgerIntegrityChecker integrityChecker,
      LedgerTransactionRepository transactions,
      LedgerEntryRepository entries) {
    this.integrityChecker = integrityChecker;
    this.transactions = transactions;
    this.entries = entries;
  }

  @Transactional(readOnly = true)
  public LedgerAuditReport audit() {
    LedgerIntegrityChecker.Result integrity = integrityChecker.check();
    long totalTxns = transactions.count();
    long totalPostings = entries.count();
    Instant now = Instant.now();

    LedgerAuditReport.AuditStatus status =
        integrity.balanced()
            ? LedgerAuditReport.AuditStatus.VERIFIED_HEALTHY
            : LedgerAuditReport.AuditStatus.IMBALANCE_DETECTED;

    String fingerprint = computeSolvencyFingerprint(totalTxns, totalPostings, integrity.ledgerNet(), now);

    return new LedgerAuditReport(
        status,
        integrity.ledgerNet(),
        integrity.driftedAccounts().size(),
        integrity.driftedAccounts(),
        totalTxns,
        totalPostings,
        fingerprint,
        now);
  }

  private String computeSolvencyFingerprint(
      long totalTxns, long totalPostings, long netVariance, Instant timestamp) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      String input =
          "txns="
              + totalTxns
              + ";postings="
              + totalPostings
              + ";variance="
              + netVariance
              + ";bucket="
              + timestamp.truncatedTo(ChronoUnit.HOURS);
      byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return "0x" + HexFormat.of().formatHex(hash);
    } catch (Exception e) {
      return "0x" + Long.toHexString(totalTxns) + Long.toHexString(totalPostings);
    }
  }
}
