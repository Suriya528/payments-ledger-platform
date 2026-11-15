package com.suriya.payments.repository;

import com.suriya.payments.domain.IdempotencyRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, Long> {

  Optional<IdempotencyRecord> findByMerchantIdAndIdemKey(String merchantId, String idemKey);
}
