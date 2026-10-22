package ru.quietclinic.patients;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_consent")
class Consent {
  @Id UUID id;

  @jakarta.persistence.ManyToOne(optional = false)
  Patient patient;

  @Column(nullable = false)
  String version;

  @Column(nullable = false)
  String basis;

  @Column(nullable = false)
  Instant grantedAt;

  Instant revokedAt;

  protected Consent() {}

  Consent(Patient patient, String version, String basis) {
    id = UUID.randomUUID();
    this.patient = patient;
    this.version = version;
    this.basis = basis;
    grantedAt = Instant.now();
  }
}
