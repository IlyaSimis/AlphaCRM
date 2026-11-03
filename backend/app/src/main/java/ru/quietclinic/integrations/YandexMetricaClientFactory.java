package ru.quietclinic.integrations;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Загружает конфигурацию Метрики только внутри указанной клиники. */
@Component
class YandexMetricaClientFactory {
  private final IntegrationRepository repository;
  private final IntegrationSecretCipher cipher;
  private final JsonMapper json;

  YandexMetricaClientFactory(
      IntegrationRepository repository, IntegrationSecretCipher cipher, JsonMapper json) {
    this.repository = repository;
    this.cipher = cipher;
    this.json = json;
  }

  Optional<Connection> forClinic(UUID clinicId) {
    return repository.find(clinicId, IntegrationService.YANDEX_METRICA).map(this::load);
  }

  void requireValid(IntegrationRecord integration) {
    Connection connection = load(integration);
    if (connection.counterId().isBlank() || connection.oauthToken().isBlank()) {
      throw conflict(
          "YANDEX_METRICA_CONFIGURATION_REQUIRED", "Counter ID and OAuth token are required");
    }
  }

  private Connection load(IntegrationRecord integration) {
    YandexMetricaSettings settings =
        json.readValue(integration.settings(), YandexMetricaSettings.class);
    String token = "";
    if (integration.activeSecretVersion() != null) {
      int version = integration.activeSecretVersion();
      IntegrationSecretRecord sealed =
          repository
              .findSecret(integration.id(), version)
              .orElseThrow(
                  () -> new IllegalStateException("Integration secret version is missing"));
      token =
          json.readValue(
                  cipher.decrypt(integration.clinicId(), integration.id(), version, sealed),
                  YandexMetricaSecrets.class)
              .oauthToken();
    }
    return new Connection(
        settings.apiUrl(), settings.counterId(), token, integration.status());
  }

  record Connection(String apiUrl, String counterId, String oauthToken, String status) {}
}
