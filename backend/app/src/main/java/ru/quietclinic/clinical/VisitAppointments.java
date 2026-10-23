package ru.quietclinic.clinical;

import java.util.Optional;
import java.util.UUID;

/**
 * Порт к записям на приём. Реализует модуль расписания, поэтому clinical от scheduling не зависит.
 */
public interface VisitAppointments {
  Optional<AppointmentSnapshot> find(UUID appointmentId);

  /**
   * Приём начат: запись из «Ожидает», «Подтверждён» или «Прибыл» переходит в IN_PROGRESS. Другие
   * записи врача не трогаются — одновременно на приёме может быть несколько пациентов.
   */
  void markInProgress(UUID appointmentId);
}
