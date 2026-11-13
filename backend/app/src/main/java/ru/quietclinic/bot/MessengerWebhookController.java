package ru.quietclinic.bot;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Публичные webhook Telegram и MAX. publicKey уже проверен фильтром до разбора события. */
@RestController
class MessengerWebhookController {
  private final MessengerWebhookService incoming;

  MessengerWebhookController(MessengerWebhookService incoming) {
    this.incoming = incoming;
  }

  @PostMapping("/api/v1/integrations/telegram/events/{publicKey}")
  WebhookAck telegram(@PathVariable UUID publicKey, @RequestBody JsonNode update) {
    incoming.telegram(update);
    return new WebhookAck(true);
  }

  @PostMapping("/api/v1/integrations/max/events/{publicKey}")
  WebhookAck max(@PathVariable UUID publicKey, @RequestBody JsonNode update) {
    incoming.max(update);
    return new WebhookAck(true);
  }

  record WebhookAck(boolean accepted) {}
}
