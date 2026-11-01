package ru.quietclinic.communications;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.quietclinic.integrations.MessengerIntegrationClientFactory;
import ru.quietclinic.integrations.MessengerSecrets;

/** Создаёт канал из DB-конфигурации клиники; вызывающий код решает, допустим ли legacy fallback. */
@Component
class MessengerChannelFactory {
  private final MessengerIntegrationClientFactory integrations;
  private final HttpTransport http;
  private final NoticeText texts;

  MessengerChannelFactory(
      MessengerIntegrationClientFactory integrations, HttpTransport http, NoticeText texts) {
    this.integrations = integrations;
    this.http = http;
    this.texts = texts;
  }

  Optional<TelegramChannel> telegram(UUID clinicId) {
    return integrations
        .forClinic(clinicId, TelegramChannel.NAME)
        .map(
            integration -> {
              var settings = integration.settings();
              var secrets =
                  integration.secrets() == null
                      ? new MessengerSecrets("", "", "")
                      : integration.secrets();
              var buttons =
                  settings.reminderButtons()
                      ? new String[] {
                        texts.label("telegram-button-confirm"),
                        texts.label("telegram-button-decline")
                      }
                      : null;
              return new TelegramChannel(
                  http,
                  secrets.botToken(),
                  settings.apiUrl(),
                  buttons,
                  settings.relayUrl(),
                  secrets.relaySecret(),
                  integration.active());
            });
  }

  Optional<MaxChannel> max(UUID clinicId) {
    return integrations
        .forClinic(clinicId, MaxChannel.NAME)
        .map(
            integration -> {
              var settings = integration.settings();
              var token = integration.secrets() == null ? "" : integration.secrets().botToken();
              return new MaxChannel(
                  http, token, settings.apiUrl(), settings.reminderButtons(), integration.active());
            });
  }
}
