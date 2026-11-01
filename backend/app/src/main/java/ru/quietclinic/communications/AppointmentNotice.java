package ru.quietclinic.communications;

import java.time.Instant;
import java.util.UUID;

/** Данные записи для сообщения пациенту: из таблиц записи, врача, клиники и пациента. */
record AppointmentNotice(
    UUID appointmentId,
    UUID patientId,
    String status,
    Instant startsAt,
    String serviceName,
    String practitionerName,
    String clinicName,
    boolean activeConsent,
    String telegramChatId,
    String maxUserId,
    String preferredMessenger,
    UUID clinicId) {

  AppointmentNotice(
      UUID appointmentId,
      UUID patientId,
      String status,
      Instant startsAt,
      String serviceName,
      String practitionerName,
      String clinicName,
      boolean activeConsent,
      String telegramChatId,
      String maxUserId,
      String preferredMessenger) {
    this(
        appointmentId,
        patientId,
        status,
        startsAt,
        serviceName,
        practitionerName,
        clinicName,
        activeConsent,
        telegramChatId,
        maxUserId,
        preferredMessenger,
        null);
  }

  AppointmentNotice(
      UUID appointmentId,
      UUID patientId,
      String status,
      Instant startsAt,
      String serviceName,
      String practitionerName,
      String clinicName,
      boolean activeConsent,
      String telegramChatId) {
    this(
        appointmentId,
        patientId,
        status,
        startsAt,
        serviceName,
        practitionerName,
        clinicName,
        activeConsent,
        telegramChatId,
        null,
        null,
        null);
  }
}
