package ru.quietclinic.scheduling;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClinicServiceRepository extends JpaRepository<ClinicService, UUID> {
  Optional<ClinicService> findByName(String name);

  List<ClinicService> findAllByOrderByDurationMinutesDesc();
}
