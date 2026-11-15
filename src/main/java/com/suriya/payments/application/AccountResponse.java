package com.suriya.payments.application;

import com.suriya.payments.domain.Account;
import com.suriya.payments.domain.AccountType;
import com.suriya.payments.domain.OwnerType;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    OwnerType ownerType,
    String ownerId,
    AccountType type,
    String currency,
    Instant createdAt) {

  public static AccountResponse from(Account account) {
    return new AccountResponse(
        account.getId(),
        account.getOwnerType(),
        account.getOwnerId(),
        account.getType(),
        account.getCurrency(),
        account.getCreatedAt());
  }
}
