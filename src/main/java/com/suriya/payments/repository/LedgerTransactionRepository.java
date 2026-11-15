package com.suriya.payments.repository;

import com.suriya.payments.domain.LedgerTransaction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {}
