package ru.quietclinic.bot;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import ru.quietclinic.patients.MessengerChannel;
import ru.quietclinic.patients.PatientMessengerLinks;
import tools.jackson.databind.JsonNode;

/** Привязка карточки и кнопки напоминаний; остальные события принимаются без изменений. */
@Service
class MessengerWebhookService {
  private static final Pattern START =
      Pattern.compile("^/start(?:@[A-Za-z0-9_]+)?\\s+([A-Za-z0-9_-]{20,128})$");
  private static final Pattern CALLBACK = Pattern.compile("^cf:([0-9a-fA-F-]{36}):(yes|no)$");

  private final BotService bot;
  private final PatientMessengerLinks links;

  MessengerWebhookService(BotService bot, PatientMessengerLinks links) {
    this.bot = bot;
    this.links = links;
  }

  void telegram(JsonNode update) {
    JsonNode callback = update.path("callback_query");
    if (!callback.isMissingNode() && !callback.isNull()) {
      String chatId = text(callback.path("message").path("chat"), "id");
      if (chatId.isEmpty()) chatId = text(callback.path("from"), "id");
      action(MessengerChannel.TELEGRAM, chatId, text(callback, "data"));
      return;
    }
    JsonNode message = update.path("message");
    Matcher matcher = START.matcher(text(message, "text").trim());
    if (matcher.matches()) {
      links.consume(MessengerChannel.TELEGRAM, text(message.path("chat"), "id"), matcher.group(1));
    }
  }

  void max(JsonNode update) {
    switch (text(update, "update_type")) {
      case "bot_started" -> consume(text(update.path("user"), "user_id"), text(update, "payload"));
      case "message_created" -> {
        JsonNode message = update.path("message");
        Matcher matcher = START.matcher(text(message.path("body"), "text").trim());
        if (matcher.matches()) consume(text(message.path("sender"), "user_id"), matcher.group(1));
      }
      case "message_callback" -> {
        JsonNode callback = update.path("callback");
        action(
            MessengerChannel.MAX,
            text(callback.path("user"), "user_id"),
            text(callback, "payload"));
      }
      default -> {
        // Подписка может включать события, которые CRM пока не обрабатывает.
      }
    }
  }

  private void consume(String externalId, String token) {
    if (!externalId.isEmpty() && token.matches("^[A-Za-z0-9_-]{20,128}$")) {
      links.consume(MessengerChannel.MAX, externalId, token);
    }
  }

  private void action(MessengerChannel channel, String externalId, String payload) {
    Matcher matcher = CALLBACK.matcher(payload);
    if (externalId.isEmpty() || !matcher.matches()) return;
    UUID appointmentId = UUID.fromString(matcher.group(1));
    if ("yes".equals(matcher.group(2))) {
      bot.confirm(appointmentId, channel, externalId);
    } else if (!"CANCELLED".equals(bot.appointment(appointmentId, channel, externalId).status())) {
      // Повторная доставка уже обработанной отмены не должна зацикливать webhook.
      bot.cancel(appointmentId, channel, externalId);
    }
  }

  private static String text(JsonNode node, String field) {
    return node.path(field).asText("");
  }
}
