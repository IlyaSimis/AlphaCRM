package ru.quietclinic.integrations;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Загружает конфигурацию мессенджера клиники; отсутствие записи разрешает legacy fallback. */
@Component
public class MessengerIntegrationClientFactory {
  private final IntegrationRepository repository;
  private final IntegrationSecretCipher cipher;
  private final JsonMapper json;
  private final ru.quietclinic.tenancy.TenantDirectory directory;

  MessengerIntegrationClientFactory(
      IntegrationRepository repository,
      IntegrationSecretCipher cipher,
      JsonMapper json,
      ru.quietclinic.tenancy.TenantDirectory directory) {
    this.repository = repository;
    this.cipher = cipher;
    this.json = json;
    this.directory = directory;
  }

  public Optional<MessengerIntegration> forClinic(UUID clinicId, String type) {
    if (!IntegrationService.TELEGRAM.equals(type) && !IntegrationService.MAX.equals(type)) {
      throw new IllegalArgumentException("Unsupported messenger integration");
    }
    return repository.find(clinicId, type).map(this::load);
  }

  /** Сначала управляющий каталог выбирает базу, затем секрет ищется только внутри неё. */
  public Optional<MessengerWebhook> webhook(UUID publicKey, String type) {
    UUID tenantId = directory.clinicForWebhook(publicKey, type).orElse(null);
    if (tenantId == null) return webhookInTenant(publicKey, type);
    return ru.quietclinic.tenancy.TenantScope.call(
        tenantId, () -> webhookInTenant(publicKey, type));
  }

  private Optional<MessengerWebhook> webhookInTenant(UUID publicKey, String type) {
    return repository
        .findByPublicKey(publicKey, type)
        .filter(integration -> "ACTIVE".equals(integration.status()))
        .flatMap(
            integration -> {
              MessengerSecrets secrets = load(integration).secrets();
              if (secrets == null
                  || secrets.webhookSecret() == null
                  || secrets.webhookSecret().isBlank()) {
                return Optional.empty();
              }
              return Optional.of(
                  new MessengerWebhook(integration.clinicId(), secrets.webhookSecret()));
            });
  }

  void requireValid(IntegrationRecord integration) {
    MessengerIntegration loaded = load(integration);
    if (loaded.secrets() == null) {
      throw conflict("INTEGRATION_SECRET_MISSING", "Integration secret is required");
    }
    MessengerSettings settings = loaded.settings();
    MessengerSecrets secrets = loaded.secrets();
    if (IntegrationService.TELEGRAM.equals(integration.type()) && !settings.relayUrl().isEmpty()) {
      if (secrets.relaySecret().isBlank()) {
        throw conflict("INTEGRATION_SECRET_MISSING", "Telegram relay secret is required");
      }
    } else if (secrets.botToken().isBlank()) {
      throw conflict("INTEGRATION_SECRET_MISSING", "Bot token is required");
    }
  }

  private MessengerIntegration load(IntegrationRecord integration) {
    MessengerSettings settings = json.readValue(integration.settings(), MessengerSettings.class);
    MessengerSecrets secrets = null;
    if (integration.activeSecretVersion() != null) {
      Integer version = integration.activeSecretVersion();
      IntegrationSecretRecord sealed =
          repository
              .findSecret(integration.id(), version)
              .orElseThrow(
                  () -> new IllegalStateException("Integration secret version is missing"));
      secrets =
          json.readValue(
              cipher.decrypt(integration.clinicId(), integration.id(), version, sealed),
              MessengerSecrets.class);
    }
    return new MessengerIntegration(
        integration.publicKey(), settings, secrets, "ACTIVE".equals(integration.status()));
  }
}
