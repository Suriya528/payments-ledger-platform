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
import com.suriya.payments.infrastructure.security.MerchantPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Create an account (Admin only)", description = "Creates a new ledger account (e.g. USER_WALLET, MERCHANT_PAYABLE) with an initial zero balance.")
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
    validateAccountAccess(id);
    Account account = accountService.getAccount(id);
    return ResponseEntity.ok(AccountResponse.from(account));
  }

  @PostMapping("/{id}/fund")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Fund a wallet (Admin only - Double-Entry Deposit)",
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
    validateAccountAccess(id);
    return accountQuery.getBalance(id);
  }

  @GetMapping("/{id}/ledger")
  @Operation(summary = "Get account ledger postings", description = "Retrieves an immutable audit log of postings for this account.")
  public LedgerPageResponse ledger(
      @Parameter(description = "Account UUID") @PathVariable UUID id,
      @Parameter(description = "Max records to return") @RequestParam(required = false) Integer limit) {
    validateAccountAccess(id);
    return accountQuery.getLedger(id, limit);
  }

  private void validateAccountAccess(UUID accountId) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof MerchantPrincipal mp) {
      Account account = accountService.getAccount(accountId);
      if (!account.getOwnerId().equals(mp.merchantId())) {
        throw new AccessDeniedException(
            "Merchant %s is not authorized to access account %s".formatted(mp.merchantId(), accountId));
      }
    }
  }
}
