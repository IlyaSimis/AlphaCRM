package ru.quietclinic.patients;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PatientRepository extends JpaRepository<Patient, UUID> {
  List<Patient> findByClinicIdOrderByFullName(UUID clinicId);

  List<Patient> findTop20ByClinicIdAndFullNameContainingIgnoreCaseOrderByFullName(
      UUID clinicId, String query);

  Optional<Patient> findByIdAndClinicId(UUID id, UUID clinicId);
}
