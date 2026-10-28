package ru.quietclinic.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MarkingOperationRepository extends JpaRepository<MarkingOperation, UUID> {
  List<MarkingOperation> findTop200ByClinicIdOrderByCreatedAtDesc(UUID clinicId);

  List<MarkingOperation> findTop200ByClinicIdAndStatusOrderByCreatedAtDesc(
      UUID clinicId, String status);

  List<MarkingOperation> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
      String status, java.time.Instant now);

  Optional<MarkingOperation> findByIdAndClinicId(UUID id, UUID clinicId);

  long countByClinicIdAndStatus(UUID clinicId, String status);
}
