package ru.quietclinic.billing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentRepository extends JpaRepository<PaymentRecord, UUID> {
  Optional<PaymentRecord> findByIdempotencyKey(String key);

  Optional<PaymentRecord> findFirstByChargeIdOrderByCreatedAtDesc(UUID chargeId);
}
