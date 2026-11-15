package com.suriya.payments.api;

import com.suriya.payments.application.AccountQueryService;
import com.suriya.payments.application.AccountResponse;
import com.suriya.payments.application.AccountService;
import com.suriya.payments.application.BalanceResponse;
import com.suriya.payments.application.CreateAccountRequest;
import com.suriya.payments.application.FundAccountRequest;
import com.suriya.payments.application.FundAccountResponse;
import com.suriya.payments.application.LedgerPageResponse;
import com.suriya.payments.domain.Account;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/accounts")
@Tag(name = "Accounts & Wallets", description = "Account creation, balance inquiries, ledger journals, and wallet funding")
public class AccountController {

  private final AccountQueryService accountQuery;
  private final AccountService accountService;

  public AccountController(AccountQueryService accountQuery, AccountService accountService) {
    this.accountQuery = accountQuery;
    this.accountService = accountService;
  }

  @PostMapping
  @Operation(summary = "Create an account", description = "Creates a new ledger account (e.g. USER_WALLET, MERCHANT_PAYABLE) with an initial zero balance.")
  public ResponseEntity<AccountResponse> createAccount(
      @Valid @RequestBody CreateAccountRequest request) {
    Account account =
        accountService.createAccount(
            request.ownerType(), request.ownerId(), request.type(), request.currency());
    AccountResponse response = AccountResponse.from(account);
    return ResponseEntity.created(URI.create("/v1/accounts/" + account.getId())).body(response);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get account details", description = "Retrieves an account's metadata by ID.")
  public ResponseEntity<AccountResponse> getAccount(
      @Parameter(description = "Account UUID") @PathVariable UUID id) {
    Account account = accountService.getAccount(id);
    return ResponseEntity.ok(AccountResponse.from(account));
  }

  @PostMapping("/{id}/fund")
  @Operation(
      summary = "Fund a wallet (Double-Entry Deposit)",
      description = "Deposits money into an account using a balanced double-entry transaction against system reserve.")
  public ResponseEntity<FundAccountResponse> fundAccount(
      @Parameter(description = "Account UUID") @PathVariable UUID id,
      @Valid @RequestBody FundAccountRequest request) {
    FundAccountResponse response = accountService.fundAccount(id, request.amount());
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{id}/balance")
  @Operation(summary = "Get materialized balance", description = "Reads the fast cached balance row for an account.")
  public BalanceResponse balance(
      @Parameter(description = "Account UUID") @PathVariable UUID id) {
    return accountQuery.getBalance(id);
  }

  @GetMapping("/{id}/ledger")
  @Operation(summary = "Get account ledger postings", description = "Retrieves an immutable audit log of postings for this account.")
  public LedgerPageResponse ledger(
      @Parameter(description = "Account UUID") @PathVariable UUID id,
      @Parameter(description = "Max records to return") @RequestParam(required = false) Integer limit) {
    return accountQuery.getLedger(id, limit);
  }
}
