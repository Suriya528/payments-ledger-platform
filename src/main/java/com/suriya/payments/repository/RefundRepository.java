package com.suriya.payments.repository;

import com.suriya.payments.domain.Refund;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

  long countByPaymentId(UUID paymentId);
}
