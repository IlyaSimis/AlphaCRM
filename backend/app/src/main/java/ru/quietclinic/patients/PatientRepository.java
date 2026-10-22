package ru.quietclinic.patients;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PatientRepository extends JpaRepository<Patient, UUID> {
  List<Patient> findTop20ByFullNameContainingIgnoreCaseOrderByFullName(String query);
}
