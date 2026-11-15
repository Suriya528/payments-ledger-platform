package com.suriya.payments.application;

import com.suriya.payments.domain.Account;
import com.suriya.payments.domain.AccountBalance;
import com.suriya.payments.domain.AccountType;
import com.suriya.payments.domain.Direction;
import com.suriya.payments.domain.LedgerService;
import com.suriya.payments.domain.LedgerService.Posting;
import com.suriya.payments.domain.OwnerType;
import com.suriya.payments.domain.TransactionType;
import com.suriya.payments.repository.AccountBalanceRepository;
import com.suriya.payments.repository.AccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages accounts and wallet funding.
 * Ensures all financial injections adhere strictly to double-entry ledger invariants.
 */
@Service
public class AccountService {

  private static final long INITIAL_RESERVE = 10_000_000_000L; // 100M units

  private final AccountRepository accounts;
  private final AccountBalanceRepository balances;
  private final LedgerService ledger;

  public AccountService(
      AccountRepository accounts,
      AccountBalanceRepository balances,
      LedgerService ledger) {
    this.accounts = accounts;
    this.balances = balances;
    this.ledger = ledger;
  }

  @Transactional
  public Account createAccount(
      OwnerType ownerType, String ownerId, AccountType type, String currency) {
    Account account = new Account();
    account.setId(UUID.randomUUID());
    account.setOwnerType(ownerType);
    account.setOwnerId(ownerId);
    account.setType(type);
    account.setCurrency(currency.toUpperCase());
    account.setCreatedAt(Instant.now());
    accounts.save(account);

    AccountBalance balance = new AccountBalance();
    balance.setAccountId(account.getId());
    balance.setBalance(0L);
    balance.setUpdatedAt(Instant.now());
    balances.save(balance);

    return account;
  }

  @Transactional(readOnly = true)
  public Account getAccount(UUID id) {
    return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
  }

  /**
   * Funds an account via a balanced double-entry transaction.
   * Debits the SYSTEM_RESERVE clearing account and Credits the destination account,
   * guaranteeing that the net ledger sum remains strictly zero.
   */
  @Transactional
  public FundAccountResponse fundAccount(UUID accountId, long amount) {
    if (amount <= 0) {
      throw new IllegalArgumentException("Funding amount must be strictly positive: " + amount);
    }

    Account account = getAccount(accountId);
    Account reserve = getOrCreateSystemReserve(account.getCurrency());

    UUID transactionId = UUID.randomUUID();
    ledger.post(
        TransactionType.DEPOSIT,
        transactionId,
        List.of(
            new Posting(reserve.getId(), Direction.DEBIT, amount),
            new Posting(account.getId(), Direction.CREDIT, amount)));

    AccountBalance updatedBalance =
        balances.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));

    return new FundAccountResponse(
        transactionId, accountId, amount, updatedBalance.getBalance());
  }

  private Account getOrCreateSystemReserve(String currency) {
    return accounts
        .findFirstByType(AccountType.SYSTEM_RESERVE)
        .filter(acc -> acc.getCurrency().equalsIgnoreCase(currency))
        .orElseGet(() -> {
          Account reserve = new Account();
          reserve.setId(UUID.randomUUID());
          reserve.setOwnerType(OwnerType.SYSTEM);
          reserve.setOwnerId("central-treasury");
          reserve.setType(AccountType.SYSTEM_RESERVE);
          reserve.setCurrency(currency.toUpperCase());
          reserve.setCreatedAt(Instant.now());
          accounts.save(reserve);

          AccountBalance resBalance = new AccountBalance();
          resBalance.setAccountId(reserve.getId());
          resBalance.setBalance(INITIAL_RESERVE);
          resBalance.setUpdatedAt(Instant.now());
          balances.save(resBalance);

          return reserve;
        });
  }
}
