package ru.quietclinic.communications;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Telegram Bot API (TELEGRAM_BOT_TOKEN). Нужен контакт пациента типа TELEGRAM с chat id: его
 * пациент получает, только начав диалог с ботом, — телефона недостаточно. Токен входит в адрес
 * запроса, поэтому адрес нигде не логируется.
 */
@Component
class TelegramChannel {
  static final String NAME = "TELEGRAM";

  /** Префикс callback_data, который понимает бот клиники: «cf:<id записи>:yes|no». */
  static final String CONFIRM_CALLBACK_PREFIX = "cf";

  private final HttpTransport http;
  private final String token;
  private final String apiUrl;

  /** Надписи кнопок «Подтвердить» и «Отменить запись»; null — напоминание уходит без кнопок. */
  private final String[] reminderButtons;

  @Autowired
  TelegramChannel(
      HttpTransport http,
      NoticeText texts,
      @Value("${clinic.outbound.telegram-bot-token:}") String token,
      @Value("${clinic.outbound.telegram-api-url:https://api.telegram.org}") String apiUrl,
      @Value("${clinic.outbound.telegram-reminder-buttons:true}") boolean reminderButtons) {
    this(
        http,
        token,
        apiUrl,
        reminderButtons
            ? new String[] {
              texts.label("telegram-button-confirm"), texts.label("telegram-button-decline")
            }
            : null);
  }

  TelegramChannel(HttpTransport http, String token, String apiUrl) {
    this(http, token, apiUrl, null);
  }

  TelegramChannel(HttpTransport http, String token, String apiUrl, String[] reminderButtons) {
    this.http = http;
    this.token = token == null ? "" : token.trim();
    this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
    this.reminderButtons = reminderButtons;
  }

  boolean configured() {
    return !token.isEmpty();
  }

  DeliveryResult send(PatientMessage message) {
    if (message.telegramChatId() == null || message.telegramChatId().isBlank()) {
      return DeliveryResult.notConfigured(NAME, "NO_TELEGRAM_CONTACT");
    }
    var body = new LinkedHashMap<String, Object>();
    body.put("chat_id", message.telegramChatId());
    body.put("text", message.text());
    var markup = replyMarkup(message);
    if (markup != null) body.put("reply_markup", new RawJson(markup));
    try {
      var status =
          http.postJson(
              URI.create(apiUrl + "/bot" + token + "/sendMessage"), Json.object(body), Map.of());
      return DeliveryResult.ofHttp(NAME, status);
    } catch (IOException | IllegalArgumentException ex) {
      return DeliveryResult.retry(NAME, "NETWORK_ERROR");
    }
  }

  /**
   * Под напоминанием — кнопки «Подтвердить» и «Отменить запись». Их нажатие получает бот клиники
   * (тот же токен) и вызывает API бота в CRM. Под подтверждением и отменой кнопок нет.
   */
  String replyMarkup(PatientMessage message) {
    if (reminderButtons == null
        || !NoticeTemplates.REMINDER.equals(message.template())
        || message.appointmentId() == null) {
      return null;
    }
    var prefix = CONFIRM_CALLBACK_PREFIX + ":" + message.appointmentId() + ":";
    return "{\"inline_keyboard\":[["
        + button(reminderButtons[0], prefix + "yes")
        + ","
        + button(reminderButtons[1], prefix + "no")
        + "]]}";
  }

  private static String button(String text, String callbackData) {
    return "{\"text\":" + Json.quote(text) + ",\"callback_data\":" + Json.quote(callbackData) + "}";
  }
}
