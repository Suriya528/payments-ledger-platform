package com.suriya.payments.repository;

import com.suriya.payments.domain.Payment;
import com.suriya.payments.domain.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  long countByPayerAccount(UUID payerAccount);

  Optional<Payment> findByPspReference(String pspReference);

  List<Payment> findByStatusAndUpdatedAtBefore(PaymentStatus status, Instant cutoff);
}
