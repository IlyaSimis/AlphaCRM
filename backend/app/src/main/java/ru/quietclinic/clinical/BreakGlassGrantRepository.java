package ru.quietclinic.clinical;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BreakGlassGrantRepository extends JpaRepository<BreakGlassGrant, UUID> {
  Optional<BreakGlassGrant> findFirstByOwnerIdAndPatientIdAndExpiresAtAfterOrderByExpiresAtDesc(
      UUID ownerId, UUID patientId, Instant now);

  List<BreakGlassGrant> findByClinicIdAndGrantedAtBetweenOrderByGrantedAtDesc(
      UUID clinicId, Instant from, Instant to);
}
