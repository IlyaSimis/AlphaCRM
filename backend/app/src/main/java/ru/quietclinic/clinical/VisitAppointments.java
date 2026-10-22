package ru.quietclinic.clinical;

import java.util.Optional;
import java.util.UUID;

/**
 * Порт к записям на приём. Реализует модуль расписания, поэтому clinical от scheduling не
 * зависит.
 */
public interface VisitAppointments {
  Optional<AppointmentSnapshot> find(UUID appointmentId);
}
