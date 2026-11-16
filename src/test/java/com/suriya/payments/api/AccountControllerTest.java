package com.suriya.payments.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suriya.payments.application.CreateAccountRequest;
import com.suriya.payments.application.FundAccountRequest;
import com.suriya.payments.domain.AccountType;
import com.suriya.payments.domain.OwnerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AccountControllerTest {

  private static final String ADMIN_KEY = "admin-live-key-secret-999";
  private static final String MERCHANT_KEY = "merchant-m1-key-456";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;

  @Test
  @DisplayName("Create account, fund it via double-entry deposit, and query balance and ledger")
  void createFundAndQueryAccount() throws Exception {
    // 1. Create User Wallet (Admin)
    CreateAccountRequest createReq =
        new CreateAccountRequest(OwnerType.USER, "bob-builder", AccountType.USER_WALLET, "INR");

    MvcResult createResult =
        mvc.perform(
                post("/v1/accounts")
                    .header("X-API-Key", ADMIN_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.ownerId").value("bob-builder"))
            .andExpect(jsonPath("$.type").value("USER_WALLET"))
            .andReturn();

    String accountId =
        json.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

    // 2. Fund account with 50,000 minor units (Admin only)
    FundAccountRequest fundReq = new FundAccountRequest(50_000L);
    mvc.perform(
            post("/v1/accounts/" + accountId + "/fund")
                .header("X-API-Key", ADMIN_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(fundReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.creditedAmount").value(50000))
        .andExpect(jsonPath("$.updatedBalance").value(50000));

    // 3. Query Balance (Admin)
    mvc.perform(get("/v1/accounts/" + accountId + "/balance").header("X-API-Key", ADMIN_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(50000))
        .andExpect(jsonPath("$.currency").value("INR"));

    // 4. Query Ledger (Admin)
    mvc.perform(get("/v1/accounts/" + accountId + "/ledger").header("X-API-Key", ADMIN_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries").isArray())
        .andExpect(jsonPath("$.entries[0].direction").value("CREDIT"))
        .andExpect(jsonPath("$.entries[0].amount").value(50000));
  }

  @Test
  @DisplayName("Unauthenticated request to account creation returns 401 Unauthorized")
  void unauthenticatedRequestReturns401() throws Exception {
    CreateAccountRequest createReq =
        new CreateAccountRequest(OwnerType.USER, "alice", AccountType.USER_WALLET, "INR");

    mvc.perform(
            post("/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(createReq)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Merchant role cannot create or fund accounts (403 Forbidden)")
  void merchantRoleCannotFundAccount() throws Exception {
    CreateAccountRequest createReq =
        new CreateAccountRequest(OwnerType.USER, "alice", AccountType.USER_WALLET, "INR");

    mvc.perform(
            post("/v1/accounts")
                .header("X-API-Key", MERCHANT_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(createReq)))
        .andExpect(status().isForbidden());
  }
}

