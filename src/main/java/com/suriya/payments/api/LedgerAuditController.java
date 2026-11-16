package com.suriya.payments.api;

import com.suriya.payments.application.LedgerAuditReport;
import com.suriya.payments.application.LedgerAuditService;
import com.suriya.payments.application.ReconciliationReport;
import com.suriya.payments.application.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/ledger")
@Tag(
    name = "Ledger Audit & Solvency",
    description = "Mathematical double-entry auditing, zero-drift verification, and solvency proofs")
public class LedgerAuditController {

  private final LedgerAuditService auditService;
  private final ReconciliationService reconciliationService;

  public LedgerAuditController(
      LedgerAuditService auditService, ReconciliationService reconciliationService) {
    this.auditService = auditService;
    this.reconciliationService = reconciliationService;
  }

  @GetMapping("/audit")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Perform comprehensive ledger audit (Admin only)",
      description =
          "Mathematically verifies that sum(debit) == sum(credit) across all historical entries, checks for balance drift, and computes a SHA-256 solvency fingerprint.")
  public ResponseEntity<LedgerAuditReport> audit() {
    return ResponseEntity.ok(auditService.audit());
  }

  @PostMapping("/reconcile")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Trigger on-demand reconciliation (Admin only)",
      description =
          "Runs an immediate reconciliation cycle for uncertain in-flight payments and audits ledger balance.")
  public ResponseEntity<ReconciliationReport> reconcile() {
    return ResponseEntity.ok(reconciliationService.reconcile());
  }
}
