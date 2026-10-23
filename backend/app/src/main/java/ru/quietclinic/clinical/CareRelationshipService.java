package ru.quietclinic.clinical;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Связь врача с лечением пациента — основание доступа к клиническим данным. */
@Service
public class CareRelationshipService {
  static final Duration DEFAULT_TERM = Duration.ofDays(365);
  static final String APPOINTMENT_REASON = "appointment-booking";

  private final CareRelationshipRepository relationships;

  CareRelationshipService(CareRelationshipRepository relationships) {
    this.relationships = relationships;
  }

  /** Создаёт активную связь на год, если её ещё нет. Вызывается при записи пациента к врачу. */
  @Transactional
  public void ensureCareRelationship(UUID practitionerId, UUID patientId) {
    if (isActive(practitionerId, patientId)) return;
    var now = Instant.now();
    relationships.save(
        new CareRelationship(
            practitionerId, patientId, now, now.plus(DEFAULT_TERM), APPOINTMENT_REASON));
  }

  boolean isActive(UUID practitionerId, UUID patientId) {
    var now = Instant.now();
    return relationships.existsByPractitionerIdAndPatientIdAndValidFromBeforeAndValidUntilAfter(
        practitionerId, patientId, now.plusMillis(1), now);
  }
}
