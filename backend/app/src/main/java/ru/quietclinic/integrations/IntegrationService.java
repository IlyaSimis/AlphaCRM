package ru.quietclinic.integrations;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.badRequest;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.Permission;
import tools.jackson.databind.json.JsonMapper;

@Service
class IntegrationService {
  static final String MANGO = "MANGO";
  private static final String DEFAULT_MANGO_API_URL = "https://app.mango-office.ru";

  private final IntegrationRepository repository;
  private final IntegrationSecretCipher cipher;
  private final MangoIntegrationClientFactory mangoClients;
  private final AuditService audit;
  private final JsonMapper json;

  IntegrationService(
      IntegrationRepository repository,
      IntegrationSecretCipher cipher,
      MangoIntegrationClientFactory mangoClients,
      AuditService audit,
      JsonMapper json) {
    this.repository = repository;
    this.cipher = cipher;
    this.mangoClients = mangoClients;
    this.audit = audit;
    this.json = json;
  }

  @Transactional(readOnly = true)
  List<IntegrationView> list() {
    requirePermission(Permission.INTEGRATION_READ);
    return repository.list(clinicId()).stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  IntegrationView get(String rawType) {
    requirePermission(Permission.INTEGRATION_READ);
    return view(find(rawType));
  }

  @Transactional
  IntegrationView updateSettings(String rawType, IntegrationSettingsRequest request) {
    requirePermission(Permission.INTEGRATION_MANAGE);
    var type = type(rawType);
    var settings = normalize(request);
    var integration = getOrCreate(type);
    repository.updateSettings(integration.id(), write(settings));
    audit.record(
        actorId(),
        "INTEGRATION_SETTINGS_UPDATED",
        "INTEGRATION",
        integration.id(),
        "ALLOWED",
        type);
    return view(find(type));
  }

  @Transactional
  IntegrationView updateSecret(String rawType, IntegrationSecretRequest request) {
    requirePermission(Permission.INTEGRATION_MANAGE);
    var type = type(rawType);
    var secrets = new MangoSecrets(request.apiKey().trim(), request.apiSalt().trim());
    var integration = getOrCreate(type);
    var version =
        integration.activeSecretVersion() == null ? 1 : integration.activeSecretVersion() + 1;
    var sealed = cipher.encrypt(clinicId(), integration.id(), version, write(secrets));
    repository.saveSecret(integration.id(), version, sealed);
    audit.record(
        actorId(),
        "INTEGRATION_SECRET_ROTATED",
        "INTEGRATION",
        integration.id(),
        "ALLOWED",
        type + ":v" + version);
    return view(find(type));
  }

  @Transactional
  IntegrationView activate(String rawType) {
    requirePermission(Permission.INTEGRATION_MANAGE);
    var integration = find(rawType);
    mangoClients.requireValid(integration);
    repository.updateStatus(integration.id(), "ACTIVE");
    audit.record(
        actorId(),
        "INTEGRATION_ACTIVATED",
        "INTEGRATION",
        integration.id(),
        "ALLOWED",
        integration.type());
    return view(find(integration.type()));
  }

  @Transactional
  IntegrationView disable(String rawType) {
    requirePermission(Permission.INTEGRATION_MANAGE);
    var integration = find(rawType);
    repository.updateStatus(integration.id(), "DISABLED");
    audit.record(
        actorId(),
        "INTEGRATION_DISABLED",
        "INTEGRATION",
        integration.id(),
        "ALLOWED",
        integration.type());
    return view(find(integration.type()));
  }

  private IntegrationRecord getOrCreate(String type) {
    return repository
        .find(clinicId(), type)
        .orElseGet(
            () ->
                repository.create(
                    clinicId(), type, write(new MangoSettings(DEFAULT_MANGO_API_URL, "", ""))));
  }

  private IntegrationRecord find(String rawType) {
    var type = type(rawType);
    return repository
        .find(clinicId(), type)
        .orElseThrow(() -> notFound("INTEGRATION_NOT_FOUND", "Integration is not configured"));
  }

  private static String type(String rawType) {
    var type = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT);
    if (!MANGO.equals(type)) {
      throw badRequest("INTEGRATION_TYPE_UNSUPPORTED", "Only Mango is supported by this endpoint");
    }
    return type;
  }

  private static MangoSettings normalize(IntegrationSettingsRequest request) {
    var apiUrl = request.apiUrl() == null ? "" : request.apiUrl().trim().replaceAll("/$", "");
    var extension = request.defaultExtension() == null ? "" : request.defaultExtension().trim();
    var line = request.lineNumber() == null ? "" : request.lineNumber().trim();
    try {
      var uri = URI.create(apiUrl);
      if (!List.of("http", "https").contains(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null) {
        throw new IllegalArgumentException("Invalid API URL");
      }
    } catch (IllegalArgumentException ex) {
      throw badRequest("MANGO_API_URL_INVALID", "Mango API URL must be a valid HTTP(S) URL");
    }
    if (!extension.matches("^$|^[0-9]{1,32}$") || !line.matches("^$|^[0-9]{1,32}$")) {
      throw badRequest(
          "MANGO_SETTINGS_INVALID", "Mango extension and line number must contain digits only");
    }
    return new MangoSettings(apiUrl, extension, line);
  }

  private IntegrationView view(IntegrationRecord integration) {
    return new IntegrationView(
        integration.id(),
        integration.type(),
        integration.status(),
        integration.publicKey(),
        json.readTree(integration.settings()),
        integration.activeSecretVersion() != null,
        integration.activeSecretVersion());
  }

  private String write(Object value) {
    return json.writeValueAsString(value);
  }
}
