package ru.quietclinic.inventory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MarkingCodeRepository extends JpaRepository<MarkingCode, UUID> {
  Optional<MarkingCode> findByClinicIdAndSgtin(UUID clinicId, String sgtin);

  List<MarkingCode> findByClinicIdAndSgtinIn(UUID clinicId, Collection<String> sgtins);

  List<MarkingCode> findTop500ByClinicIdOrderByUpdatedAtDesc(UUID clinicId);

  List<MarkingCode> findTop500ByClinicIdAndStatusOrderByUpdatedAtDesc(UUID clinicId, String status);
}
