package ru.quietclinic.clinical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "encounter")
class Encounter {
  @Id UUID id;

  @Column(nullable = false)
  UUID appointmentId;

  @Column(nullable = false)
  UUID patientId;

  @Column(nullable = false)
  UUID practitionerId;

  @Column(nullable = false)
  String status;

  @Column(nullable = false)
  Instant startedAt;

  Instant completedAt;

  protected Encounter() {}

  Encounter(UUID appointmentId, UUID patientId, UUID practitionerId) {
    id = UUID.randomUUID();
    this.appointmentId = appointmentId;
    this.patientId = patientId;
    this.practitionerId = practitionerId;
    status = "IN_PROGRESS";
    startedAt = Instant.now();
  }
}
