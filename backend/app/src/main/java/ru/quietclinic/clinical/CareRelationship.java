package ru.quietclinic.clinical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "care_relationship")
class CareRelationship {
  @Id UUID id;

  @Column(nullable = false)
  UUID practitionerId;

  @Column(nullable = false)
  UUID patientId;

  @Column(nullable = false)
  Instant validFrom;

  @Column(nullable = false)
  Instant validUntil;

  @Column(nullable = false)
  String reason;

  protected CareRelationship() {}

  CareRelationship(
      UUID practitionerId, UUID patientId, Instant validFrom, Instant validUntil, String reason) {
    id = UUID.randomUUID();
    this.practitionerId = practitionerId;
    this.patientId = patientId;
    this.validFrom = validFrom;
    this.validUntil = validUntil;
    this.reason = reason;
  }
}
