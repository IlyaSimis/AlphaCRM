package ru.quietclinic.communications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Когда сообщение о записи отправлять нельзя — без базы. */
class OutboxDispatcherRulesTest {
  static final Instant STARTS = Instant.parse("2027-03-02T09:00:00Z");
  static final Instant NOW = Instant.parse("2027-03-01T10:00:00Z");

  static OutboxMessage message(String template, Instant startsAt) {
    return new OutboxMessage(
        "NOTIFICATION",
        "APPOINTMENT",
        UUID.randomUUID(),
        UUID.randomUUID(),
        template,
        template + ":k",
        startsAt,
        "{}",
        NOW);
  }

  static AppointmentNotice notice(String status, Instant startsAt, boolean consent) {
    return new AppointmentNotice(
        UUID.randomUUID(),
        UUID.randomUUID(),
        status,
        startsAt,
        "Visit",
        "Dr",
        "Clinic",
        consent,
        null);
  }

  @Test
  void reminderGoesOnlyForActiveAppointmentAtTheSameTime() {
    var reminder = message(NoticeTemplates.REMINDER, STARTS);
    assertThat(OutboxDispatcher.skipReason(reminder, notice("BOOKED", STARTS, true), NOW))
        .isNull();
    assertThat(OutboxDispatcher.skipReason(reminder, notice("CANCELLED", STARTS, true), NOW))
        .isEqualTo("APPOINTMENT_CANCELLED");
    assertThat(OutboxDispatcher.skipReason(reminder, notice("NO_SHOW", STARTS, true), NOW))
        .isEqualTo("NO_SHOW");
    assertThat(
            OutboxDispatcher.skipReason(
                reminder, notice("CONFIRMED", STARTS.plusSeconds(3600), true), NOW))
        .isEqualTo("RESCHEDULED");
    assertThat(
            OutboxDispatcher.skipReason(reminder, notice("BOOKED", STARTS, true), STARTS))
        .isEqualTo("EXPIRED");
  }

  @Test
  void consentIsCheckedFirst() {
    var confirmed = message(NoticeTemplates.CONFIRMED, STARTS);
    assertThat(OutboxDispatcher.skipReason(confirmed, notice("BOOKED", STARTS, false), NOW))
        .isEqualTo("NO_CONSENT");
    var cancelled = message(NoticeTemplates.CANCELLED, STARTS);
    assertThat(OutboxDispatcher.skipReason(cancelled, notice("CANCELLED", STARTS, false), NOW))
        .isEqualTo("NO_CONSENT");
  }

  @Test
  void cancellationGoesOnlyForCancelledAppointment() {
    var cancelled = message(NoticeTemplates.CANCELLED, STARTS);
    assertThat(OutboxDispatcher.skipReason(cancelled, notice("CANCELLED", STARTS, true), NOW))
        .isNull();
    assertThat(OutboxDispatcher.skipReason(cancelled, notice("BOOKED", STARTS, true), NOW))
        .isEqualTo("NOT_CANCELLED");
  }

  @Test
  void payloadHasOnlyTemplateReferenceAndTime() {
    var payload =
        Payloads.of(
            NoticeTemplates.REMINDER,
            "APPOINTMENT",
            UUID.fromString("00000000-0000-0000-0000-000000000301"),
            STARTS);
    assertThat(payload)
        .isEqualTo(
            "{\"template\":\"appointment-reminder\","
                + "\"appointmentId\":\"00000000-0000-0000-0000-000000000301\","
                + "\"startsAt\":\"2027-03-02T09:00:00Z\"}");
  }
}
