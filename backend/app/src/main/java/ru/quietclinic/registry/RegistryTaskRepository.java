package ru.quietclinic.registry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RegistryTaskRepository extends JpaRepository<RegistryTask, UUID> {
  List<RegistryTask> findByClinicIdAndStatusAndDueDateLessThanEqualOrderByDueDate(
      UUID clinicId, String status, LocalDate dueTo);

  List<RegistryTask> findByClinicIdAndPatientIdOrderByDueDate(UUID clinicId, UUID patientId);

  Optional<RegistryTask> findByIdAndClinicId(UUID id, UUID clinicId);
}
