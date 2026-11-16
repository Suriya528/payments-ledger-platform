package com.suriya.payments.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.suriya.payments.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class LedgerAuditControllerIT extends AbstractIntegrationTest {

  private static final String ADMIN_KEY = "admin-live-key-secret-999";
  private static final String MERCHANT_KEY = "merchant-m1-key-456";

  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("GET /v1/ledger/audit requires authentication")
  void auditRequiresAuth() throws Exception {
    mvc.perform(get("/v1/ledger/audit")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /v1/ledger/audit requires ADMIN role, rejecting merchants")
  void auditRequiresAdminRole() throws Exception {
    mvc.perform(get("/v1/ledger/audit").header("X-API-Key", MERCHANT_KEY))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("GET /v1/ledger/audit with Admin key returns verified healthy audit certificate")
  void auditSucceedsWithAdminKey() throws Exception {
    mvc.perform(get("/v1/ledger/audit").header("X-API-Key", ADMIN_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VERIFIED_HEALTHY"))
        .andExpect(jsonPath("$.netVariance").value(0))
        .andExpect(jsonPath("$.driftedAccountsCount").value(0))
        .andExpect(jsonPath("$.solvencyFingerprint").isString())
        .andExpect(jsonPath("$.auditedAt").exists());
  }

  @Test
  @DisplayName("POST /v1/ledger/reconcile with Admin key triggers reconciliation report")
  void reconcileSucceedsWithAdminKey() throws Exception {
    mvc.perform(post("/v1/ledger/reconcile").header("X-API-Key", ADMIN_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ledgerNet").value(0))
        .andExpect(jsonPath("$.driftedAccounts").isArray());
  }
}
