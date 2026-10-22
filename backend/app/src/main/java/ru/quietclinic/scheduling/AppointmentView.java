package ru.quietclinic.scheduling;

import java.time.Instant;
import java.util.UUID;

record AppointmentView(
    UUID id,
    UUID patientId,
    UUID practitionerId,
    Instant startsAt,
    Instant endsAt,
    String serviceName,
    String status,
    Long amountMinor,
    String currency,
    long version) {

  static AppointmentView of(Appointment a) {
    return new AppointmentView(
        a.id,
        a.patientId,
        a.practitionerId,
        a.startsAt,
        a.endsAt,
        a.serviceName,
        a.status,
        a.amountMinor,
        a.currency,
        a.version);
  }
}
