package ru.quietclinic.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Пациент ждёт свободного слота у врача в желаемый период. */
@Entity
@Table(name = "waitlist_entry")
class WaitlistEntry {
  @Id UUID id;

  @Column(nullable = false)
  UUID patientId;

  @Column(nullable = false)
  UUID practitionerId;

  @Column(nullable = false)
  UUID serviceId;

  @Column(nullable = false)
  LocalDate wishFrom;

  @Column(nullable = false)
  LocalDate wishTo;

  @Column(nullable = false)
  String status;

  UUID appointmentId;

  @Column(nullable = false)
  Instant createdAt;

  protected WaitlistEntry() {}

  WaitlistEntry(
      UUID patientId, UUID practitionerId, UUID serviceId, LocalDate wishFrom, LocalDate wishTo) {
    id = UUID.randomUUID();
    this.patientId = patientId;
    this.practitionerId = practitionerId;
    this.serviceId = serviceId;
    this.wishFrom = wishFrom;
    this.wishTo = wishTo;
    status = "ACTIVE";
    createdAt = Instant.now();
  }

  /** Подходит ли запись к освободившемуся слоту врача в этот день. */
  boolean matches(UUID practitioner, LocalDate day) {
    return "ACTIVE".equals(status)
        && practitionerId.equals(practitioner)
        && !day.isBefore(wishFrom)
        && !day.isAfter(wishTo);
  }
}
