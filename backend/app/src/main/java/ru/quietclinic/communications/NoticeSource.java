package ru.quietclinic.communications;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Читает запись для сообщения. Модуль связан с расписанием и пациентами только таблицами — без
 * зависимости от их кода. Читаются только поля шаблона, согласие и chat id; ни заметки, ни
 * диагноза, ни документов этот класс не видит.
 */
@Component
class NoticeSource {
  private final JdbcTemplate jdbc;

  NoticeSource(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Optional<AppointmentNotice> load(UUID appointmentId) {
    List<AppointmentNotice> rows =
        jdbc.query(
            "select a.id, a.patient_id, a.status, a.starts_at, a.service_name,"
                + " p.full_name as practitioner_name, c.name as clinic_name"
                + " from appointment a join practitioner p on p.id = a.practitioner_id"
                + " join clinic c on c.id = p.clinic_id where a.id = ?",
            (row, index) -> {
              var patientId = row.getObject("patient_id", UUID.class);
              return new AppointmentNotice(
                  row.getObject("id", UUID.class),
                  patientId,
                  row.getString("status"),
                  row.getObject("starts_at", OffsetDateTime.class).toInstant(),
                  row.getString("service_name"),
                  row.getString("practitioner_name"),
                  row.getString("clinic_name"),
                  hasActiveConsent(patientId),
                  telegramChatId(patientId));
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

  private boolean hasActiveConsent(UUID patientId) {
    var count =
        jdbc.queryForObject(
            "select count(*) from patient_consent where patient_id = ? and revoked_at is null",
            Long.class,
            patientId);
    return count != null && count > 0;
  }

  private String telegramChatId(UUID patientId) {
    var values =
        jdbc.queryForList(
            "select contact_value from patient_contact where patient_id = ? and type = 'TELEGRAM'",
            String.class,
            patientId);
    return values.isEmpty() ? null : values.get(0);
  }
}
