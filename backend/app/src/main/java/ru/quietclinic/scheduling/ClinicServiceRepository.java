package ru.quietclinic.scheduling;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClinicServiceRepository extends JpaRepository<ClinicService, UUID> {
  Optional<ClinicService> findByClinicIdAndName(UUID clinicId, String name);

  Optional<ClinicService> findByIdAndClinicId(UUID id, UUID clinicId);

  boolean existsByClinicIdAndNameIgnoreCase(UUID clinicId, String name);

  List<ClinicService> findByClinicIdOrderByActiveDescDurationMinutesDescNameAsc(UUID clinicId);
}
