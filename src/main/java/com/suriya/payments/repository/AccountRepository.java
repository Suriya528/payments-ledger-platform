package com.suriya.payments.repository;

import com.suriya.payments.domain.Account;
import com.suriya.payments.domain.AccountType;
import com.suriya.payments.domain.OwnerType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

  /** Looks up a system account by its role (e.g. the single PSP_SUSPENSE account). */
  Optional<Account> findFirstByType(AccountType type);

  Optional<Account> findByOwnerTypeAndOwnerId(OwnerType ownerType, String ownerId);
}
