package ru.quietclinic.communications;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** MAX Bot API: токен только в Authorization, адрес получателя — user_id личного диалога. */
@Component
class MaxChannel {
  static final String NAME = "MAX";
  private final HttpTransport http;
  private final String token;
  private final String apiUrl;
  private final boolean reminderButtons;
  private final boolean active;

  @Autowired
  MaxChannel(
      HttpTransport http,
      @Value("${clinic.outbound.max-bot-token:}") String token,
      @Value("${clinic.outbound.max-api-url:https://platform-api2.max.ru}") String apiUrl,
      @Value("${clinic.outbound.max-reminder-buttons:true}") boolean reminderButtons) {
    this(http, token, apiUrl, reminderButtons, true);
  }

  MaxChannel(
      HttpTransport http, String token, String apiUrl, boolean reminderButtons, boolean active) {
    this.http = http;
    this.token = token == null ? "" : token.trim();
    this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
    this.reminderButtons = reminderButtons;
    this.active = active;
  }

  boolean configured() {
    return active && !token.isEmpty();
  }

  DeliveryResult send(PatientMessage message) {
    if (message.maxUserId() == null || message.maxUserId().isBlank()) {
      return DeliveryResult.notConfigured(NAME, "NO_MAX_CONTACT");
    }
    if (!configured()) return DeliveryResult.notConfigured(NAME, "CHANNEL_NOT_CONFIGURED");
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("text", message.text());
    if (reminderButtons && NoticeTemplates.REMINDER.equals(message.template())) {
      body.put("attachments", new RawJson(keyboard(message.appointmentId().toString())));
    }
    try {
      String recipient = URLEncoder.encode(message.maxUserId(), StandardCharsets.UTF_8);
      int status =
          http.postJson(
              URI.create(apiUrl + "/messages?user_id=" + recipient),
              Json.object(body),
              Map.of("Authorization", token));
      return DeliveryResult.ofHttp(NAME, status);
    } catch (IOException | IllegalArgumentException ex) {
      return DeliveryResult.retry(NAME, "NETWORK_ERROR");
    }
  }

  static String keyboard(String appointmentId) {
    return "[{\"type\":\"inline_keyboard\",\"payload\":{\"buttons\":[["
        + "{\"type\":\"callback\",\"text\":\"Подтвердить\",\"payload\":\"cf:"
        + appointmentId
        + ":yes\"},"
        + "{\"type\":\"callback\",\"text\":\"Отменить запись\",\"payload\":\"cf:"
        + appointmentId
        + ":no\"}]]}}]";
  }
}
