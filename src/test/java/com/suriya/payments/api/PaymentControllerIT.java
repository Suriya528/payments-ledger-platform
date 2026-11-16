package com.suriya.payments.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suriya.payments.application.AccountService;
import com.suriya.payments.application.CreatePaymentRequest;
import com.suriya.payments.domain.AccountType;
import com.suriya.payments.domain.OwnerType;
import com.suriya.payments.infrastructure.psp.MockPspClient;
import com.suriya.payments.infrastructure.psp.PaymentProvider;
import com.suriya.payments.repository.AccountBalanceRepository;
import com.suriya.payments.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PaymentControllerIT extends AbstractIntegrationTest {

  private static final String MERCHANT_KEY = "merchant-m1-key-456";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired AccountService accounts;
  @Autowired com.suriya.payments.repository.AccountRepository accountRepo;
  @Autowired AccountBalanceRepository balances;
  @Autowired PaymentProvider psp;

  @AfterEach
  void tearDown() {
    if (psp instanceof MockPspClient mockPsp) {
      mockPsp.setSimulateTimeout(false);
    }
  }

  @Test
  void createReturns201AndProcessesPayment() throws Exception {
    var payer = wallet();
    var payee = merchant();
    fund(payer, 100);
    var body = json.writeValueAsString(new CreatePaymentRequest("m1", payer, payee, 30, "INR"));

    mvc.perform(
            post("/v1/payments")
                .header("X-API-Key", MERCHANT_KEY)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.amount").value(30));
  }

  @Test
  void unauthenticatedPaymentReturns401() throws Exception {
    var payer = wallet();
    var payee = merchant();
    var body = json.writeValueAsString(new CreatePaymentRequest("m1", payer, payee, 30, "INR"));

    mvc.perform(
            post("/v1/payments")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void currencyMismatchReturns422() throws Exception {
    var payer = wallet();
    var payee = merchant();
    fund(payer, 100);
    // Request is in USD while accounts are INR
    var body = json.writeValueAsString(new CreatePaymentRequest("m1", payer, payee, 30, "USD"));

    mvc.perform(
            post("/v1/payments")
                .header("X-API-Key", MERCHANT_KEY)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  @DisplayName("PSP timeout sets REQUIRES_RECONCILIATION and returns 202 Accepted, replay returns same")
  void pspTimeoutReturns202AndMaintainsIdempotency() throws Exception {
    if (psp instanceof MockPspClient mockPsp) {
      mockPsp.setSimulateTimeout(true);
    }

    var payer = wallet();
    var payee = merchant();
    fund(payer, 100);
    var idempotencyKey = UUID.randomUUID().toString();
    var body = json.writeValueAsString(new CreatePaymentRequest("m1", payer, payee, 30, "INR"));

    // First attempt: times out -> 202 ACCEPTED with status REQUIRES_RECONCILIATION
    mvc.perform(
            post("/v1/payments")
                .header("X-API-Key", MERCHANT_KEY)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("REQUIRES_RECONCILIATION"));

    // Replay attempt with same Idempotency-Key: returns cached 202 response without creating new hold
    mvc.perform(
            post("/v1/payments")
                .header("X-API-Key", MERCHANT_KEY)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status").value("REQUIRES_RECONCILIATION"));
  }

  @Test
  void missingIdempotencyKeyIsBadRequest() throws Exception {
    var payer = wallet();
    var payee = merchant();
    fund(payer, 100);
    var body = json.writeValueAsString(new CreatePaymentRequest("m1", payer, payee, 30, "INR"));

    mvc.perform(
            post("/v1/payments")
                .header("X-API-Key", MERCHANT_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  private UUID wallet() {
    return accounts
        .createAccount(OwnerType.USER, "payer-" + UUID.randomUUID(), AccountType.USER_WALLET, "INR")
        .getId();
  }

  private UUID merchant() {
    return accountRepo
        .findByOwnerTypeAndOwnerId(OwnerType.MERCHANT, "m1")
        .map(com.suriya.payments.domain.Account::getId)
        .orElseGet(
            () ->
                accounts
                    .createAccount(
                        OwnerType.MERCHANT, "m1", AccountType.MERCHANT_PAYABLE, "INR")
                    .getId());
  }

  private void fund(UUID accountId, long amount) {
    var balance = balances.findById(accountId).orElseThrow();
    balance.setBalance(amount);
    balances.saveAndFlush(balance);
  }
}

