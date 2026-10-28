package ru.quietclinic.billing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RefundRepository extends JpaRepository<RefundRecord, UUID> {
  Optional<RefundRecord> findByIdempotencyKey(String key);

  Optional<RefundRecord> findByPaymentId(UUID paymentId);

  Optional<RefundRecord> findByChargeId(UUID chargeId);
}
