package ru.quietclinic.billing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ChargeRepository extends JpaRepository<Charge, UUID> {
  Optional<Charge> findByAppointmentId(UUID appointmentId);

  List<Charge> findAllByOrderByCreatedAtDesc();
}
