package ru.quietclinic.scheduling;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.quietclinic.clinical.AppointmentSnapshot;
import ru.quietclinic.clinical.VisitAppointments;

/** Отдаёт клиническому модулю данные записи для старта приёма. */
@Component
class ClinicalAppointmentsAdapter implements VisitAppointments {
  private final AppointmentRepository appointments;

  ClinicalAppointmentsAdapter(AppointmentRepository appointments) {
    this.appointments = appointments;
  }

  @Override
  public Optional<AppointmentSnapshot> find(UUID appointmentId) {
    return appointments
        .findById(appointmentId)
        .map(a -> new AppointmentSnapshot(a.id, a.patientId, a.practitionerId, a.status));
  }
}
