package com.suriya.payments.infrastructure.seed;

import com.suriya.payments.application.AccountService;
import com.suriya.payments.domain.Account;
import com.suriya.payments.domain.AccountType;
import com.suriya.payments.domain.OwnerType;
import com.suriya.payments.repository.AccountRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Automatically provisions initial system and demo accounts for local development and live demonstrations.
 */
@Component
@Profile("local")
@ConditionalOnProperty(name = "payments.demo-seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

  private final AccountService accountService;
  private final AccountRepository accountRepository;

  public DemoDataSeeder(AccountService accountService, AccountRepository accountRepository) {
    this.accountService = accountService;
    this.accountRepository = accountRepository;
  }

  @Override
  public void run(String... args) {
    // 1. Ensure system accounts required by the payment saga exist
    accountRepository
        .findFirstByType(AccountType.PSP_SUSPENSE)
        .orElseGet(() ->
            accountService.createAccount(OwnerType.SYSTEM, "psp-clearing", AccountType.PSP_SUSPENSE, "INR"));

    accountRepository
        .findFirstByType(AccountType.FEE_INCOME)
        .orElseGet(() ->
            accountService.createAccount(OwnerType.SYSTEM, "fee-collector", AccountType.FEE_INCOME, "INR"));

    // 2. Create demo user and merchant accounts
    Account userWallet =
        accountRepository
            .findByOwnerTypeAndOwnerId(OwnerType.USER, "alice")
            .orElseGet(() -> {
              Account acc = accountService.createAccount(
                  OwnerType.USER, "alice", AccountType.USER_WALLET, "INR");
              // Fund Alice with 100,000 minor units (e.g. ₹1,000.00)
              accountService.fundAccount(acc.getId(), 100_000L);
              return acc;
            });

    Account merchantAccount =
        accountRepository
            .findByOwnerTypeAndOwnerId(OwnerType.MERCHANT, "acme-store")
            .orElseGet(() ->
                accountService.createAccount(
                    OwnerType.MERCHANT, "acme-store", AccountType.MERCHANT_PAYABLE, "INR"));

    printWelcomeBanner(userWallet.getId(), merchantAccount.getId());
  }

  private void printWelcomeBanner(UUID aliceWalletId, UUID acmeMerchantId) {
    log.info("\n"
        + "========================================================================================\n"
        + "  PAYMENTS & DOUBLE-ENTRY WALLET SERVICE READY!\n"
        + "========================================================================================\n"
        + "  Interactive Swagger UI : http://localhost:8080/swagger-ui.html\n"
        + "  Prometheus Metrics    : http://localhost:8080/actuator/prometheus\n"
        + "  Health Check          : http://localhost:8080/actuator/health\n"
        + "----------------------------------------------------------------------------------------\n"
        + "  Pre-seeded Demo Accounts:\n"
        + "  - User Wallet (Alice) : {}\n"
        + "    Balance             : 100,000 minor units (1000.00 INR)\n"
        + "  - Merchant (Acme)     : {}\n"
        + "----------------------------------------------------------------------------------------\n"
        + "  Quick Test cURL (Idempotent Payment):\n"
        + "  curl -X POST http://localhost:8080/v1/payments \\\n"
        + "    -H \"Content-Type: application/json\" \\\n"
        + "    -H \"Idempotency-Key: $(uuidgen)\" \\\n"
        + "    -d '{{\"merchantId\":\"acme-store\",\"payerAccountId\":\"{}\",\"payeeAccountId\":\"{}\",\"amount\":2500,\"currency\":\"INR\"}}'\n"
        + "========================================================================================",
        aliceWalletId, acmeMerchantId, aliceWalletId, acmeMerchantId);
  }
}
