package com.suriya.payments.api;

import com.suriya.payments.application.IntegrityResponse;
import com.suriya.payments.application.LedgerIntegrityChecker;
import com.suriya.payments.application.ReconciliationReport;
import com.suriya.payments.application.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/reconciliation")
@Tag(name = "Reconciliation & Audit", description = "Continuous audit of ledger balance invariants and stale hold expiration")
public class ReconciliationController {

  private final LedgerIntegrityChecker integrity;
  private final ReconciliationService reconciliationService;

  public ReconciliationController(
      LedgerIntegrityChecker integrity,
      ReconciliationService reconciliationService) {
    this.integrity = integrity;
    this.reconciliationService = reconciliationService;
  }

  @GetMapping("/report")
  @Operation(
      summary = "Check ledger balance integrity",
      description =
          "Audits that sum(debits) - sum(credits) == 0 across all postings, and verifies that materialized account balances match historical postings.")
  public IntegrityResponse report() {
    LedgerIntegrityChecker.Result result = integrity.check();
    return new IntegrityResponse(
        result.balanced(), result.ledgerNet(), result.driftedAccounts().size());
  }

  @PostMapping("/run")
  @Operation(
      summary = "Trigger on-demand reconciliation job",
      description =
          "Runs an audit scan and expires stale AUTHORIZED holds whose asynchronous webhook capture was never received.")
  public ResponseEntity<ReconciliationReport> runNow() {
    ReconciliationReport report = reconciliationService.reconcile();
    return ResponseEntity.ok(report);
  }
}
