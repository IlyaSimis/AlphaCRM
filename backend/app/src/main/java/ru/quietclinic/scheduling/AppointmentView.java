package ru.quietclinic.scheduling;

import java.time.Instant;
import java.util.UUID;

/**
 * Запись в расписании. patientId пуст для ролей без доступа к пациентам (техподдержка видит
 * расписание с маской: время, врач, статус). При свободной цене amountMinor пуст, priceMode = OPEN.
 */
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
    long version,
    UUID serviceId,
    String priceMode,
    Long estimateMinor) {

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
        a.version,
        a.serviceId,
        a.priceMode,
        a.estimateMinor);
  }

  /** Без идентификатора пациента — для маски расписания. */
  AppointmentView masked() {
    return new AppointmentView(
        id,
        null,
        practitionerId,
        startsAt,
        endsAt,
        serviceName,
        status,
        amountMinor,
        currency,
        version,
        serviceId,
        priceMode,
        estimateMinor);
  }
}
