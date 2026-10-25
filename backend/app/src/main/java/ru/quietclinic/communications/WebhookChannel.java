package ru.quietclinic.communications;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Вебхук клиники (CLINIC_OUTBOUND_WEBHOOK_URL): POST с полями шаблона, подписью HMAC и ключом
 * идемпотентности. Так клиника подключает своего бота, не встраивая его в CRM.
 */
@Component
class WebhookChannel {
  static final String NAME = "WEBHOOK";

  private final HttpTransport http;
  private final String url;
  private final String secret;

  WebhookChannel(
      HttpTransport http,
      @Value("${clinic.outbound.webhook-url:}") String url,
      @Value("${clinic.outbound.webhook-secret:}") String secret) {
    this.http = http;
    this.url = url == null ? "" : url.trim();
    this.secret = secret == null ? "" : secret;
  }

  boolean configured() {
    return !url.isEmpty();
  }

  DeliveryResult send(PatientMessage message) {
    if (secret.isBlank()) return DeliveryResult.notConfigured(NAME, "WEBHOOK_SECRET_MISSING");
    var body = body(message);
    var timestamp = Instant.now().getEpochSecond();
    try {
      var status =
          http.postJson(
              URI.create(url),
              body,
              Map.of(
                  Signature.IDEMPOTENCY_HEADER, message.idempotencyKey(),
                  Signature.TIMESTAMP_HEADER, Long.toString(timestamp),
                  Signature.SIGNATURE_HEADER, Signature.sign(secret, timestamp, body)));
      return DeliveryResult.ofHttp(NAME, status);
    } catch (IOException | IllegalArgumentException ex) {
      return DeliveryResult.retry(NAME, "NETWORK_ERROR");
    }
  }

  /** Тело вебхука: поля шаблона и готовый текст; из адресов — только chat id Telegram. */
  static String body(PatientMessage message) {
    var recipient = new LinkedHashMap<String, Object>();
    recipient.put("patientId", message.patientId().toString());
    recipient.put("telegramChatId", message.telegramChatId());
    var fields = new LinkedHashMap<String, Object>();
    fields.put("idempotencyKey", message.idempotencyKey());
    fields.put("template", message.template());
    fields.put("appointmentId", message.appointmentId().toString());
    fields.put("clinic", message.clinicName());
    fields.put("service", message.serviceName());
    fields.put("practitioner", message.practitionerName());
    fields.put("startsAt", message.startsAt().toString());
    fields.put("date", message.date());
    fields.put("time", message.time());
    fields.put("text", message.text());
    fields.put("recipient", new RawJson(Json.object(recipient)));
    return Json.object(fields);
  }
}
