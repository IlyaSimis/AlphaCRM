package ru.quietclinic.clinical;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PatientFileRepository extends JpaRepository<PatientFile, UUID> {
  Optional<PatientFile> findByIdAndClinicId(UUID id, UUID clinicId);
}
