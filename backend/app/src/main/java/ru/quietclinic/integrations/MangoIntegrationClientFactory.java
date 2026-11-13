package ru.quietclinic.integrations;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Создаёт Mango client по клинике; пустой результат разрешает временный legacy fallback. */
@Component
public class MangoIntegrationClientFactory {
  private final IntegrationRepository repository;
  private final IntegrationSecretCipher cipher;
  private final JsonMapper json;
  private final ru.quietclinic.tenancy.TenantDirectory directory;

  MangoIntegrationClientFactory(
      IntegrationRepository repository,
      IntegrationSecretCipher cipher,
      JsonMapper json,
      ru.quietclinic.tenancy.TenantDirectory directory) {
    this.repository = repository;
    this.cipher = cipher;
    this.json = json;
    this.directory = directory;
  }

  /** Запись в реестре всегда важнее env: PENDING/DISABLED не проваливаются в legacy. */
  public Optional<MangoClient> forClinic(UUID clinicId) {
    return repository.find(clinicId, IntegrationService.MANGO).map(this::client);
  }

  /** Публичный ключ выбирает только активную полностью настроенную интеграцию в её базе. */
  public Optional<MangoWebhook> webhook(UUID publicKey) {
    UUID tenantId = directory.clinicForWebhook(publicKey, IntegrationService.MANGO).orElse(null);
    if (tenantId == null) return webhookInTenant(publicKey);
    return ru.quietclinic.tenancy.TenantScope.call(tenantId, () -> webhookInTenant(publicKey));
  }

  private Optional<MangoWebhook> webhookInTenant(UUID publicKey) {
    return repository
        .findByPublicKey(publicKey, IntegrationService.MANGO)
        .filter(integration -> "ACTIVE".equals(integration.status()))
        .flatMap(
            integration -> {
              Loaded loaded = load(integration);
              if (loaded.secrets() == null
                  || loaded.secrets().apiKey().isBlank()
                  || loaded.secrets().apiSalt().isBlank()) {
                return Optional.empty();
              }
              return Optional.of(
                  new MangoWebhook(
                      integration.clinicId(),
                      loaded.secrets().apiKey(),
                      loaded.secrets().apiSalt()));
            });
  }

  void requireValid(IntegrationRecord integration) {
    Loaded loaded = load(integration);
    if (loaded.secrets() == null) {
      throw conflict("INTEGRATION_SECRET_MISSING", "Integration secret is required");
    }
  }

  private MangoClient client(IntegrationRecord integration) {
    Loaded loaded = load(integration);
    return new ScopedMangoClient(
        json, loaded.settings(), loaded.secrets(), "ACTIVE".equals(integration.status()));
  }

  private Loaded load(IntegrationRecord integration) {
    MangoSettings settings = json.readValue(integration.settings(), MangoSettings.class);
    MangoSecrets secrets = null;
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
              MangoSecrets.class);
    }
    return new Loaded(settings, secrets);
  }

  private record Loaded(MangoSettings settings, MangoSecrets secrets) {}
}
