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
  private final AppointmentService service;

  ClinicalAppointmentsAdapter(AppointmentRepository appointments, AppointmentService service) {
    this.appointments = appointments;
    this.service = service;
  }

  @Override
  public Optional<AppointmentSnapshot> find(UUID appointmentId) {
    return appointments
        .findById(appointmentId)
        .map(a -> new AppointmentSnapshot(a.id, a.patientId, a.practitionerId, a.status));
  }

  @Override
  public void markInProgress(UUID appointmentId) {
    service.markInProgress(appointmentId);
  }
}
