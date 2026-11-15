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

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;

  @Test
  @DisplayName("Create account, fund it via double-entry deposit, and query balance and ledger")
  void createFundAndQueryAccount() throws Exception {
    // 1. Create User Wallet
    CreateAccountRequest createReq =
        new CreateAccountRequest(OwnerType.USER, "bob-builder", AccountType.USER_WALLET, "INR");

    MvcResult createResult =
        mvc.perform(
                post("/v1/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.ownerId").value("bob-builder"))
            .andExpect(jsonPath("$.type").value("USER_WALLET"))
            .andReturn();

    String accountId =
        json.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

    // 2. Fund account with 50,000 minor units
    FundAccountRequest fundReq = new FundAccountRequest(50_000L);
    mvc.perform(
            post("/v1/accounts/" + accountId + "/fund")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(fundReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.creditedAmount").value(50000))
        .andExpect(jsonPath("$.updatedBalance").value(50000));

    // 3. Query Balance
    mvc.perform(get("/v1/accounts/" + accountId + "/balance"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance").value(50000))
        .andExpect(jsonPath("$.currency").value("INR"));

    // 4. Query Ledger
    mvc.perform(get("/v1/accounts/" + accountId + "/ledger"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries").isArray())
        .andExpect(jsonPath("$.entries[0].direction").value("CREDIT"))
        .andExpect(jsonPath("$.entries[0].amount").value(50000));
  }
}
