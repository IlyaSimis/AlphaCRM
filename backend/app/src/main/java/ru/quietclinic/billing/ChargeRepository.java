package ru.quietclinic.billing;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ChargeRepository extends JpaRepository<Charge, UUID> {
  Optional<Charge> findByAppointmentId(UUID appointmentId);

  List<Charge> findAllByOrderByCreatedAtDesc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Charge c where c.id = :id")
  Optional<Charge> findLockedById(@Param("id") UUID id);
}
