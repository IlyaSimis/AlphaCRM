package ru.quietclinic.billing;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PaymentRepository extends JpaRepository<PaymentRecord, UUID> {
  Optional<PaymentRecord> findByIdempotencyKey(String key);

  Optional<PaymentRecord> findFirstByChargeIdOrderByCreatedAtDesc(UUID chargeId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PaymentRecord p where p.providerRef = :reference")
  Optional<PaymentRecord> findLockedByProviderRef(@Param("reference") String reference);
}
