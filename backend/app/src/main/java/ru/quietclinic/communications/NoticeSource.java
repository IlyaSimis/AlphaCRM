package ru.quietclinic.communications;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.quietclinic.patients.PatientContactGateway;

/**
 * Читает запись для сообщения. Модуль связан с расписанием и пациентами только таблицами — без
 * зависимости от их кода. Читаются только поля шаблона, согласие и chat id; ни заметки, ни
 * диагноза, ни документов этот класс не видит.
 */
@Component
class NoticeSource {
  private final JdbcTemplate jdbc;
  private final PatientContactGateway patientContacts;

  NoticeSource(JdbcTemplate jdbc, PatientContactGateway patientContacts) {
    this.jdbc = jdbc;
    this.patientContacts = patientContacts;
  }

  Optional<AppointmentNotice> load(UUID appointmentId) {
    List<AppointmentNotice> rows =
        jdbc.query(
            "select a.id, a.patient_id, a.status, a.starts_at, a.service_name,"
                + " p.full_name as practitioner_name, c.name as clinic_name, c.id as clinic_id,"
                + " pt.preferred_messenger"
                + " from appointment a join practitioner p on p.id = a.practitioner_id"
                + " join clinic c on c.id = p.clinic_id join patient pt on pt.id = a.patient_id"
                + " where a.id = ?",
            (row, index) -> {
              UUID patientId = row.getObject("patient_id", UUID.class);
              return new AppointmentNotice(
                  row.getObject("id", UUID.class),
                  patientId,
                  row.getString("status"),
                  row.getObject("starts_at", OffsetDateTime.class).toInstant(),
                  row.getString("service_name"),
                  row.getString("practitioner_name"),
                  row.getString("clinic_name"),
                  hasActiveConsent(patientId),
                  contact(patientId, "TELEGRAM"),
                  contact(patientId, "MAX"),
                  row.getString("preferred_messenger"),
                  row.getObject("clinic_id", UUID.class));
            },
            appointmentId);
    return rows.stream().findFirst();
  }

  /** Записи, у которых время сменилось после постановки подтверждения (перенос). */
  List<UUID> rescheduledAppointments(Instant now) {
    return jdbc.queryForList(
        "select distinct a.id from outbox_message m join appointment a on a.id = m.aggregate_id"
            + " where m.topic = 'NOTIFICATION' and m.template = 'appointment-confirmed'"
            + " and a.status in ('BOOKED', 'CONFIRMED') and a.starts_at > ?"
            + " and m.appointment_starts_at <> a.starts_at"
            + " and not exists (select 1 from outbox_message n where n.aggregate_id = a.id"
            + " and n.template = 'appointment-confirmed'"
            + " and n.appointment_starts_at = a.starts_at)",
        UUID.class,
        now.atOffset(ZoneOffset.UTC));
  }

  /**
   * Будущие активные записи без напоминания на текущее время. Восстанавливает очередь для записей,
   * созданных миграцией или до появления outbox; уже отправленное напоминание тоже учитывается.
   */
  List<MissingReminder> missingReminders(Instant now) {
    return jdbc.query(
        "select a.id, p.clinic_id, a.starts_at from appointment a"
            + " join practitioner p on p.id = a.practitioner_id"
            + " left join clinic_notification_settings s on s.clinic_id = p.clinic_id"
            + " where a.status in ('BOOKED', 'CONFIRMED') and a.starts_at > ?"
            + " and ((coalesce(s.reminder_24h_enabled, true) and not exists"
            + " (select 1 from outbox_message m where m.aggregate_id = a.id"
            + " and m.template = 'appointment-reminder'"
            + " and m.appointment_starts_at = a.starts_at))"
            + " or (coalesce(s.reminder_3h_enabled, false) and not exists"
            + " (select 1 from outbox_message m where m.aggregate_id = a.id"
            + " and m.template = 'appointment-reminder-3h'"
            + " and m.appointment_starts_at = a.starts_at)))"
            + " order by a.starts_at limit 200",
        (row, index) ->
            new MissingReminder(
                row.getObject("id", UUID.class),
                row.getObject("clinic_id", UUID.class),
                row.getObject("starts_at", OffsetDateTime.class).toInstant()),
        now.atOffset(ZoneOffset.UTC));
  }

  record MissingReminder(UUID appointmentId, UUID clinicId, Instant startsAt) {}

  private boolean hasActiveConsent(UUID patientId) {
    Long count =
        jdbc.queryForObject(
            "select count(*) from patient_consent where patient_id = ? and revoked_at is null",
            Long.class,
            patientId);
    return count != null && count > 0;
  }

  private String contact(UUID patientId, String channel) {
    return patientContacts.value(patientId, channel).orElse(null);
  }
}
