package com.suriya.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.suriya.payments.application.AccountService;
import com.suriya.payments.domain.Account;
import com.suriya.payments.domain.AccountType;
import com.suriya.payments.domain.OwnerType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class LocalBootstrapTest {

  @Autowired private AccountService accountService;

  @Test
  void contextLoadsAndCanCreateAccount() {
    Account account =
        accountService.createAccount(
            OwnerType.USER, "user-test", AccountType.USER_WALLET, "INR");
    assertThat(account).isNotNull();
    assertThat(account.getId()).isNotNull();
  }
}
