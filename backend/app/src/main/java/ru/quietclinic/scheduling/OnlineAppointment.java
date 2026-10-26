package ru.quietclinic.scheduling;

import java.time.Instant;
import java.util.UUID;

/** Запись в том виде, в каком её видит бот: без цены и версии. */
public record OnlineAppointment(
    UUID id,
    UUID patientId,
    UUID practitionerId,
    Instant startsAt,
    Instant endsAt,
    String serviceName,
    String status) {

  static OnlineAppointment of(AppointmentView a) {
    return new OnlineAppointment(
        a.id(), a.patientId(), a.practitionerId(), a.startsAt(), a.endsAt(), a.serviceName(), a.status());
  }
}
