package ru.quietclinic.communications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.quietclinic.StaffSessions;
import ru.quietclinic.audit.AuditService;

/**
 * Доставщик outbox с поддельным каналом: проверяется, что и когда уходит пациенту. Фоновый запуск в
 * тестах выключен (src/test/resources/application.properties), доставщик зовётся вручную.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class OutboxDispatcherTest {
  static final String DMITRY = "00000000-0000-0000-0000-000000000102";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired OutboxRepository outbox;
  @Autowired OutboxService service;
  @Autowired NoticeSource notices;
  @Autowired NoticeText text;
  @Autowired AuditService audit;
  @Autowired TransactionTemplate tx;

  StaffSessions staff;
  RecordingPort port;
  OutboxDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    staff = new StaffSessions(mvc);
    port = new RecordingPort();
    dispatcher =
        new OutboxDispatcher(
            outbox,
            service,
            notices,
            text,
            port,
            (paymentId, key) -> FiscalResult.notConfigured(),
            audit,
            tx);
  }

  @Test
  void repeatedRunDoesNotSendTheSameMessageTwice() throws Exception {
    String appointment = book(DMITRY, "2027-02-02T09:00:00Z", "2027-02-02T09:30:00Z");
    drain(Instant.now());
    drain(Instant.now());

    List<PatientMessage> sent = port.sentFor(appointment, NoticeTemplates.CONFIRMED);
    assertThat(sent).hasSize(1);
    PatientMessage message = sent.get(0);
    assertThat(message.text()).contains("02.02.2027", "09:00", "Повторный приём");
    assertThat(message.idempotencyKey())
        .isEqualTo(
            OutboxService.key(
                NoticeTemplates.CONFIRMED,
                UUID.fromString(appointment),
                Instant.parse("2027-02-02T09:00:00Z")));
    OutboxMessage row = row(appointment, NoticeTemplates.CONFIRMED);
    assertThat(row.status).isEqualTo("SENT");
    assertThat(row.channel).isEqualTo(RecordingPort.CHANNEL);

    // В аудит — факт и id, без текста сообщения и без телефона.
    List<String> reasons =
        jdbc.queryForList(
            "select reason from audit_event where action = 'NOTIFICATION_DELIVERED'"
                + " and object_id = ?",
            String.class,
            row.id);
    assertThat(reasons).containsExactly("appointment-confirmed:test");
  }

  @Test
  void nothingIsSentWithoutActiveConsent() throws Exception {
    String created =
        mvc.perform(
                post("/api/v1/patients")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fullName":"No Consent","birthDate":"1980-02-02","phone":"+70000000077",
                         "consentVersion":"v1.2","consentBasis":"SIGNED_FORM"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String patient = JsonPath.read(created, "$.id");
    String consent = JsonPath.read(created, "$.consents[0].id");
    mvc.perform(
            post("/api/v1/patients/" + patient + "/consents/" + consent + "/revoke")
                .with(staff.admin()))
        .andExpect(status().isOk());

    String appointment = book(patient, "2027-02-04T09:00:00Z", "2027-02-04T09:30:00Z");
    drain(Instant.now());
    drain(Instant.parse("2027-02-03T10:00:00Z"));

    assertThat(port.sentFor(appointment, null)).isEmpty();
    OutboxMessage row = row(appointment, NoticeTemplates.CONFIRMED);
    assertThat(row.status).isEqualTo("SKIPPED");
    assertThat(row.lastError).isEqualTo("NO_CONSENT");
  }

  @Test
  void sendsReminderAtDueTimeOnlyOnce() throws Exception {
    String appointment = book(DMITRY, "2027-02-04T16:30:00Z", "2027-02-04T17:00:00Z");

    drain(Instant.parse("2027-02-03T16:29:00Z"));
    assertThat(port.sentFor(appointment, NoticeTemplates.REMINDER)).isEmpty();

    drain(Instant.parse("2027-02-03T16:30:00Z"));
    drain(Instant.parse("2027-02-03T16:31:00Z"));

    assertThat(port.sentFor(appointment, NoticeTemplates.REMINDER)).hasSize(1);
    assertThat(row(appointment, NoticeTemplates.REMINDER).status).isEqualTo("SENT");
  }

  @Test
  void sendsShortReminderWhenEnabledAndStopsSchedulingItWhenDisabled() throws Exception {
    jdbc.update(
        "insert into clinic_notification_settings(clinic_id, reminder_24h_enabled,"
            + " reminder_3h_enabled, updated_at) values"
            + " ('00000000-0000-0000-0000-00000000c001', true, true, CURRENT_TIMESTAMP)");
    String appointment = book(DMITRY, "2027-02-11T16:30:00Z", "2027-02-11T17:00:00Z");
    assertThat(row(appointment, NoticeTemplates.REMINDER_3H).nextAttemptAt)
        .isEqualTo(Instant.parse("2027-02-11T13:30:00Z"));

    drain(Instant.parse("2027-02-11T13:29:00Z"));
    assertThat(port.sentFor(appointment, NoticeTemplates.REMINDER_3H)).isEmpty();
    drain(Instant.parse("2027-02-11T13:30:00Z"));
    drain(Instant.parse("2027-02-11T13:31:00Z"));
    assertThat(port.sentFor(appointment, NoticeTemplates.REMINDER_3H)).hasSize(1);

    jdbc.update(
        "update clinic_notification_settings set reminder_3h_enabled = false"
            + " where clinic_id = '00000000-0000-0000-0000-00000000c001'");
    String withoutShortReminder =
        book(DMITRY, "2027-02-16T16:30:00Z", "2027-02-16T17:00:00Z");
    assertThat(outbox.findByAggregateIdOrderByCreatedAt(UUID.fromString(withoutShortReminder)))
        .filteredOn(message -> NoticeTemplates.REMINDER_3H.equals(message.template))
        .isEmpty();
  }

  @Test
  void cancelledAppointmentIsNotReminded() throws Exception {
    String appointment = book(DMITRY, "2027-02-09T09:00:00Z", "2027-02-09T09:30:00Z");
    assertThat(row(appointment, NoticeTemplates.REMINDER).nextAttemptAt)
        .isEqualTo(Instant.parse("2027-02-08T09:00:00Z"));
    mvc.perform(post("/api/v1/appointments/" + appointment + "/cancel").with(staff.admin()))
        .andExpect(status().isOk());

    drain(Instant.parse("2027-02-08T10:00:00Z"));

    assertThat(port.sentFor(appointment, NoticeTemplates.REMINDER)).isEmpty();
    assertThat(port.sentFor(appointment, NoticeTemplates.CONFIRMED)).isEmpty();
    assertThat(port.sentFor(appointment, NoticeTemplates.CANCELLED)).hasSize(1);
    OutboxMessage reminder = row(appointment, NoticeTemplates.REMINDER);
    assertThat(reminder.status).isEqualTo("CANCELLED");
    assertThat(reminder.lastError).isEqualTo("APPOINTMENT_CANCELLED");
  }

  @Test
  void rescheduleReplacesUnsentConfirmationWithOneForTheNewTime() throws Exception {
    String appointment = book(DMITRY, "2027-02-11T09:00:00Z", "2027-02-11T09:30:00Z");
    mvc.perform(
            post("/api/v1/appointments/" + appointment + "/reschedule")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"practitionerId":"%s","startsAt":"2027-02-11T11:00:00Z",
                     "endsAt":"2027-02-11T11:30:00Z"}
                    """
                        .formatted(StaffSessions.PETROV)))
        .andExpect(status().isOk());
    // Перенос сам в outbox ничего не кладёт.
    assertThat(outbox.findByAggregateIdOrderByCreatedAt(UUID.fromString(appointment))).hasSize(2);

    // Первый проход находит перенос и ставит сообщения на новое время, второй их доставляет.
    drain(Instant.now());
    drain(Instant.now().plusMillis(1));

    List<PatientMessage> sent = port.sentFor(appointment, NoticeTemplates.CONFIRMED);
    assertThat(sent).hasSize(1);
    assertThat(sent.get(0).text()).contains("11:00").doesNotContain("09:00");
    List<OutboxMessage> rows =
        outbox.findByAggregateIdOrderByCreatedAt(UUID.fromString(appointment));
    assertThat(rows)
        .filteredOn(m -> NoticeTemplates.ABOUT_TIME.contains(m.template))
        .filteredOn(m -> m.appointmentStartsAt.equals(Instant.parse("2027-02-11T09:00:00Z")))
        .allSatisfy(
            m -> {
              assertThat(m.status).isEqualTo("CANCELLED");
              assertThat(m.lastError).isEqualTo("RESCHEDULED");
            });
  }

  @Test
  void restoresOneReminderForAnExistingFutureAppointment() {
    UUID existing = UUID.randomUUID();
    UUID cancelled = UUID.randomUUID();
    jdbc.update(
        "insert into appointment(id, patient_id, practitioner_id, starts_at, ends_at, service_name,"
            + " status, created_at, version) values (?, ?, ?,"
            + " timestamp with time zone '2027-02-10 09:00:00+00',"
            + " timestamp with time zone '2027-02-10 09:30:00+00',"
            + " 'Повторный приём', 'BOOKED', CURRENT_TIMESTAMP, 0)",
        existing,
        UUID.fromString(DMITRY),
        UUID.fromString(StaffSessions.PETROV));
    jdbc.update(
        "insert into appointment(id, patient_id, practitioner_id, starts_at, ends_at, service_name,"
            + " status, created_at, version) values (?, ?, ?,"
            + " timestamp with time zone '2027-02-10 11:00:00+00',"
            + " timestamp with time zone '2027-02-10 11:30:00+00',"
            + " 'Повторный приём', 'CANCELLED', CURRENT_TIMESTAMP, 0)",
        cancelled,
        UUID.fromString(DMITRY),
        UUID.fromString(StaffSessions.PETROV));

    dispatcher.reconcileMissingReminders(Instant.parse("2027-02-01T09:00:00Z"));
    dispatcher.reconcileMissingReminders(Instant.parse("2027-02-01T09:00:00Z"));

    List<OutboxMessage> reminders =
        outbox.findByAggregateIdOrderByCreatedAt(existing).stream()
            .filter(message -> NoticeTemplates.REMINDER.equals(message.template))
            .toList();
    assertThat(reminders).hasSize(1);
    assertThat(reminders.get(0).nextAttemptAt).isEqualTo(Instant.parse("2027-02-09T09:00:00Z"));
    assertThat(outbox.findByAggregateIdOrderByCreatedAt(cancelled)).isEmpty();
  }

  @Test
  void storedPayloadAndMessageCarryNoClinicalText() throws Exception {
    String appointment = book(DMITRY, "2027-02-16T09:00:00Z", "2027-02-16T09:30:00Z");
    mvc.perform(
            post("/api/v1/appointments/" + appointment + "/status")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ARRIVED\"}"))
        .andExpect(status().isOk());
    String started =
        mvc.perform(
                post("/api/v1/clinical/encounters")
                    .with(staff.doctor(StaffSessions.PETROV))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"appointmentId":"%s","patientId":"%s","practitionerId":"%s"}
                        """
                            .formatted(appointment, DMITRY, StaffSessions.PETROV)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String encounter = JsonPath.read(started, "$.encounterId");
    mvc.perform(
            put("/api/v1/clinical/encounters/" + encounter + "/note")
                .with(staff.doctor(StaffSessions.PETROV))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"complaints":"Secret complaints","mentalStatus":"Secret status",
                     "diagnosis":"Secret diagnosis","recommendations":"Secret advice"}
                    """))
        .andExpect(status().isOk());

    drain(Instant.now());

    List<OutboxMessage> rows =
        outbox.findByAggregateIdOrderByCreatedAt(UUID.fromString(appointment));
    assertThat(rows).isNotEmpty();
    assertThat(rows)
        .allSatisfy(
            m -> {
              assertThat(m.payload).doesNotContain("Secret", "complaints", "diagnosis", "note");
              assertThat(JsonPath.<Map<String, Object>>read(m.payload, "$").keySet())
                  .containsExactlyInAnyOrder("template", "appointmentId", "startsAt");
            });
    assertThat(port.sent)
        .allSatisfy(m -> assertThat(m.text()).doesNotContain("Secret", "+7", "Дмитрий"));
  }

  /** Повторяет проходы, пока очередь общих тестовых данных не опустеет на этот момент. */
  private void drain(Instant now) {
    for (int i = 0; i < 100 && dispatcher.runOnce(now) > 0; i++) {
      // следующий проход
    }
  }

  private OutboxMessage row(String appointment, String template) {
    return outbox.findByAggregateIdOrderByCreatedAt(UUID.fromString(appointment)).stream()
        .filter(m -> m.template.equals(template))
        .reduce((first, second) -> second)
        .orElseThrow();
  }

  private String book(String patient, String startsAt, String endsAt) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/appointments")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"patientId":"%s","practitionerId":"%s","startsAt":"%s","endsAt":"%s",
                         "serviceName":"Повторный приём"}
                        """
                            .formatted(patient, StaffSessions.PETROV, startsAt, endsAt)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }
}
