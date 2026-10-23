package ru.quietclinic.registry;

import java.time.LocalDate;
import java.util.UUID;

record WaitlistView(
    UUID id,
    UUID patientId,
    UUID practitionerId,
    UUID serviceId,
    LocalDate wishFrom,
    LocalDate wishTo,
    String status,
    UUID appointmentId) {

  static WaitlistView of(WaitlistEntry e) {
    return new WaitlistView(
        e.id,
        e.patientId,
        e.practitionerId,
        e.serviceId,
        e.wishFrom,
        e.wishTo,
        e.status,
        e.appointmentId);
  }
}
