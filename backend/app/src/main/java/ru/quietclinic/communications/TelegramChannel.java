package ru.quietclinic.communications;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
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

  private final HttpTransport http;
  private final String token;
  private final String apiUrl;

  TelegramChannel(
      HttpTransport http,
      @Value("${clinic.outbound.telegram-bot-token:}") String token,
      @Value("${clinic.outbound.telegram-api-url:https://api.telegram.org}") String apiUrl) {
    this.http = http;
    this.token = token == null ? "" : token.trim();
    this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
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
    try {
      var status =
          http.postJson(
              URI.create(apiUrl + "/bot" + token + "/sendMessage"), Json.object(body), Map.of());
      return DeliveryResult.ofHttp(NAME, status);
    } catch (IOException | IllegalArgumentException ex) {
      return DeliveryResult.retry(NAME, "NETWORK_ERROR");
    }
  }
}
