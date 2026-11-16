package com.suriya.payments.infrastructure.config;

import com.suriya.payments.domain.Account;
import com.suriya.payments.domain.AccountBalance;
import com.suriya.payments.domain.AccountType;
import com.suriya.payments.domain.OwnerType;
import com.suriya.payments.repository.AccountBalanceRepository;
import com.suriya.payments.repository.AccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures system accounts (PSP_SUSPENSE, FEE_INCOME) and their materialized balances
 * exist on application startup across all profiles (PostgreSQL migrations or H2 memory).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SystemAccountsInitializer implements ApplicationListener<ContextRefreshedEvent> {

  private static final Logger log = LoggerFactory.getLogger(SystemAccountsInitializer.class);

  public static final UUID PSP_SUSPENSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  public static final UUID FEE_INCOME_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

  private final AccountRepository accounts;
  private final AccountBalanceRepository balances;

  public SystemAccountsInitializer(AccountRepository accounts, AccountBalanceRepository balances) {
    this.accounts = accounts;
    this.balances = balances;
  }

  @Override
  @Transactional
  public void onApplicationEvent(ContextRefreshedEvent event) {
    ensureSystemAccount(PSP_SUSPENSE_ID, AccountType.PSP_SUSPENSE, "psp");
    ensureSystemAccount(FEE_INCOME_ID, AccountType.FEE_INCOME, "fees");
  }

  private void ensureSystemAccount(UUID id, AccountType type, String ownerId) {
    if (accounts.findFirstByType(type).isEmpty()) {
      Account acc = new Account();
      acc.setId(id);
      acc.setOwnerType(OwnerType.SYSTEM);
      acc.setOwnerId(ownerId);
      acc.setType(type);
      acc.setCurrency("INR");
      acc.setCreatedAt(Instant.now());
      accounts.save(acc);

      if (balances.findById(id).isEmpty()) {
        AccountBalance balance = new AccountBalance();
        balance.setAccountId(id);
        balance.setBalance(0L);
        balance.setUpdatedAt(Instant.now());
        balances.save(balance);
      }
      log.info("Provisioned system account {} ({})", type, id);
    }
  }
}
