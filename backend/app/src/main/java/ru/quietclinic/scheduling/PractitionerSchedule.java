package ru.quietclinic.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "practitioner_schedule")
class PractitionerSchedule {
  @Id UUID id;

  @Column(nullable = false)
  UUID practitionerId;

  @Column(nullable = false)
  short dayOfWeek;

  @Column(nullable = false)
  LocalTime startsAt;

  @Column(nullable = false)
  LocalTime endsAt;

  protected PractitionerSchedule() {}

  PractitionerSchedule(UUID practitionerId, int dayOfWeek, LocalTime startsAt, LocalTime endsAt) {
    id = UUID.randomUUID();
    this.practitionerId = practitionerId;
    this.dayOfWeek = (short) dayOfWeek;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
  }
}
