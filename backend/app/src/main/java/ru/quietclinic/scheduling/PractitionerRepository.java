package ru.quietclinic.scheduling;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PractitionerRepository extends JpaRepository<Practitioner, UUID> {
  List<Practitioner> findByClinicIdOrderByFullName(UUID clinicId);

  Optional<Practitioner> findByIdAndClinicId(UUID id, UUID clinicId);

  boolean existsByIdAndClinicId(UUID id, UUID clinicId);
}
