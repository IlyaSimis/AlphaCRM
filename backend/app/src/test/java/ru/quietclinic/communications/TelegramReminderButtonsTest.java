package ru.quietclinic.communications;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Кнопки под напоминанием в Telegram: их нажатие разбирает бот клиники («cf:<id>:yes|no»). */
class TelegramReminderButtonsTest {
  static final String APPOINTMENT = "00000000-0000-0000-0000-000000000301";

  static PatientMessage message(String template) {
    return new PatientMessage(
        template + ":x:1",
        template,
        UUID.fromString(APPOINTMENT),
        UUID.fromString("00000000-0000-0000-0000-000000000101"),
        "Clinic",
        "Follow-up visit",
        "Dr Petrov",
        Instant.parse("2027-02-02T09:00:00Z"),
        "2027-02-02",
        "09:00",
        "Reminder",
        "12345");
  }

  @Test
  void reminderCarriesConfirmAndDeclineButtons() {
    var channel =
        new TelegramChannel(
            new HttpTransport(),
            "token",
            "https://api.telegram.org",
            new String[] {"Подтвердить", "Отменить запись"});
    assertThat(channel.replyMarkup(message(NoticeTemplates.REMINDER)))
        .isEqualTo(
            "{\"inline_keyboard\":[[{\"text\":\"Подтвердить\",\"callback_data\":\"cf:"
                + APPOINTMENT
                + ":yes\"},{\"text\":\"Отменить запись\",\"callback_data\":\"cf:"
                + APPOINTMENT
                + ":no\"}]]}");
    assertThat(channel.replyMarkup(message(NoticeTemplates.CONFIRMED))).isNull();
    assertThat(channel.replyMarkup(message(NoticeTemplates.CANCELLED))).isNull();
  }

  @Test
  void noButtonsWhenSwitchedOff() {
    var channel = new TelegramChannel(new HttpTransport(), "token", "https://api.telegram.org");
    assertThat(channel.replyMarkup(message(NoticeTemplates.REMINDER))).isNull();
  }

  @Test
  void callbackDataFitsTelegramLimitAndLabelsComeFromNoticeTexts() {
    // Telegram принимает callback_data не длиннее 64 байт.
    assertThat(("cf:" + APPOINTMENT + ":yes").getBytes(StandardCharsets.UTF_8).length)
        .isLessThanOrEqualTo(64);
    var texts = new NoticeText("UTC");
    assertThat(texts.label("telegram-button-confirm")).isEqualTo("Подтвердить");
    assertThat(texts.label("telegram-button-decline")).isEqualTo("Отменить запись");
  }
}
