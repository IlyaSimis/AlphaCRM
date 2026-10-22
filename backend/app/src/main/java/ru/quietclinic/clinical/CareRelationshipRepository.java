package ru.quietclinic.clinical;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CareRelationshipRepository extends JpaRepository<CareRelationship, UUID> {
  boolean existsByPractitionerIdAndPatientIdAndValidFromBeforeAndValidUntilAfter(
      UUID practitionerId, UUID patientId, Instant now1, Instant now2);
}
