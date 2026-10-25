package ru.quietclinic.communications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebhookChannelTest {
  static PatientMessage message(String chatId) {
    return new PatientMessage(
        "appointment-confirmed:x:1",
        "appointment-confirmed",
        UUID.fromString("00000000-0000-0000-0000-000000000301"),
        UUID.fromString("00000000-0000-0000-0000-000000000101"),
        "Clinic \"North\"",
        "Follow-up visit",
        "Dr Petrov",
        Instant.parse("2027-02-02T09:00:00Z"),
        "2027-02-02",
        "09:00",
        "You are booked\nat 09:00",
        chatId);
  }

  @Test
  void bodyCarriesTemplateFieldsAndOnlyTheTelegramChatIdAsAddress() {
    var body = WebhookChannel.body(message("12345"));
    assertThat(body)
        .contains("\"template\":\"appointment-confirmed\"")
        .contains("\"idempotencyKey\":\"appointment-confirmed:x:1\"")
        .contains("\"clinic\":\"Clinic \\\"North\\\"\"")
        .contains("\"text\":\"You are booked\\nat 09:00\"")
        .contains("\"recipient\":{\"patientId\":\"00000000-0000-0000-0000-000000000101\"")
        .contains("\"telegramChatId\":\"12345\"")
        .doesNotContain("phone");
    assertThat(WebhookChannel.body(message(null))).contains("\"telegramChatId\":null");
  }

  @Test
  void toStringHidesTextAndAddress() {
    assertThat(message("12345").toString()).doesNotContain("12345", "booked");
  }

  @Test
  void httpStatusDecidesBetweenSentRetryAndRejected() {
    assertThat(DeliveryResult.ofHttp("WEBHOOK", 204).outcome()).isEqualTo(DeliveryOutcome.SENT);
    assertThat(DeliveryResult.ofHttp("WEBHOOK", 503).outcome()).isEqualTo(DeliveryOutcome.RETRY);
    assertThat(DeliveryResult.ofHttp("WEBHOOK", 429).outcome()).isEqualTo(DeliveryOutcome.RETRY);
    assertThat(DeliveryResult.ofHttp("TELEGRAM", 403).outcome())
        .isEqualTo(DeliveryOutcome.REJECTED);
  }

  @Test
  void withoutAnyChannelTheMockLeavesTheMessagePending() {
    var port =
        new ConfiguredNotificationPort(
            new WebhookChannel(new HttpTransport(), "", ""),
            new TelegramChannel(new HttpTransport(), "", "https://api.telegram.org"),
            new SafeMockNotificationAdapter());
    var result = port.deliver(message("12345"));
    assertThat(result.outcome()).isEqualTo(DeliveryOutcome.NOT_CONFIGURED);
    assertThat(result.reason()).isEqualTo("CHANNEL_NOT_CONFIGURED");
    assertThat(port.channelFor(message("12345"))).isEqualTo("MOCK");
  }

  @Test
  void telegramNeedsAChatIdNotJustAPhone() {
    var telegram = new TelegramChannel(new HttpTransport(), "token", "https://api.telegram.org");
    var result = telegram.send(message(null));
    assertThat(result.outcome()).isEqualTo(DeliveryOutcome.NOT_CONFIGURED);
    assertThat(result.reason()).isEqualTo("NO_TELEGRAM_CONTACT");
  }
}
