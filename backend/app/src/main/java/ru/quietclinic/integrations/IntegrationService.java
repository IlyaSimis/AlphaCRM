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
  static final String TELEGRAM = "TELEGRAM";
  static final String MAX = "MAX";
  private static final String DEFAULT_MANGO_API_URL = "https://app.mango-office.ru";
  private static final String DEFAULT_TELEGRAM_API_URL = "https://api.telegram.org";
  private static final String DEFAULT_MAX_API_URL = "https://platform-api2.max.ru";

  private final IntegrationRepository repository;
  private final IntegrationSecretCipher cipher;
  private final MangoIntegrationClientFactory mangoClients;
  private final MessengerIntegrationClientFactory messengerClients;
  private final MessengerWebhookRegistrationService webhookRegistration;
  private final AuditService audit;
  private final JsonMapper json;
  private final ru.quietclinic.tenancy.TenantDirectory directory;

  IntegrationService(
      IntegrationRepository repository,
      IntegrationSecretCipher cipher,
      MangoIntegrationClientFactory mangoClients,
      MessengerIntegrationClientFactory messengerClients,
      MessengerWebhookRegistrationService webhookRegistration,
      AuditService audit,
      JsonMapper json,
      ru.quietclinic.tenancy.TenantDirectory directory) {
    this.repository = repository;
    this.cipher = cipher;
    this.mangoClients = mangoClients;
    this.messengerClients = messengerClients;
    this.webhookRegistration = webhookRegistration;
    this.audit = audit;
    this.json = json;
    this.directory = directory;
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
    String type = type(rawType);
    Object settings = normalize(type, request);
    IntegrationRecord integration = getOrCreate(type);
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
    String type = type(rawType);
    IntegrationRecord integration = getOrCreate(type);
    MessengerSecrets previous =
        MANGO.equals(type)
            ? null
            : messengerClients
                .forClinic(clinicId(), type)
                .map(MessengerIntegration::secrets)
                .orElse(null);
    Object secrets = secrets(type, request, previous);
    int version =
        integration.activeSecretVersion() == null ? 1 : integration.activeSecretVersion() + 1;
    IntegrationSecretCipher.Sealed sealed =
        cipher.encrypt(clinicId(), integration.id(), version, write(secrets));
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
    IntegrationRecord integration = find(rawType);
    if (MANGO.equals(integration.type())) mangoClients.requireValid(integration);
    else messengerClients.requireValid(integration);
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
    IntegrationRecord integration = find(rawType);
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

  IntegrationWebhookStatusView connectWebhook(String rawType) {
    requirePermission(Permission.INTEGRATION_MANAGE);
    var status = webhookRegistration.connect(rawType);
    var integration = find(rawType);
    audit.record(
        actorId(),
        "INTEGRATION_WEBHOOK_CONNECTED",
        "INTEGRATION",
        integration.id(),
        "ALLOWED",
        integration.type());
    return status;
  }

  IntegrationWebhookStatusView webhookStatus(String rawType) {
    requirePermission(Permission.INTEGRATION_READ);
    return webhookRegistration.status(rawType);
  }

  private IntegrationRecord getOrCreate(String type) {
    return repository
        .find(clinicId(), type)
        .orElseGet(
            () -> {
              IntegrationRecord integration =
                  repository.create(clinicId(), type, write(defaultSettings(type)));
              directory.registerWebhook(clinicId(), integration.publicKey(), type);
              return integration;
            });
  }

  private IntegrationRecord find(String rawType) {
    String type = type(rawType);
    return repository
        .find(clinicId(), type)
        .orElseThrow(() -> notFound("INTEGRATION_NOT_FOUND", "Integration is not configured"));
  }

  private static String type(String rawType) {
    String type = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT);
    if (!List.of(MANGO, TELEGRAM, MAX).contains(type)) {
      throw badRequest(
          "INTEGRATION_TYPE_UNSUPPORTED", "Only Mango, Telegram and MAX are supported");
    }
    return type;
  }

  private static Object normalize(String type, IntegrationSettingsRequest request) {
    String apiUrl = text(request.apiUrl()).replaceAll("/$", "");
    requireUrl(apiUrl, true, "INTEGRATION_API_URL_INVALID");
    if (MANGO.equals(type)) {
      String extension = text(request.defaultExtension());
      String line = text(request.lineNumber());
      if (!extension.matches("^$|^[0-9]{1,32}$") || !line.matches("^$|^[0-9]{1,32}$")) {
        throw badRequest(
            "MANGO_SETTINGS_INVALID", "Mango extension and line number must contain digits only");
      }
      return new MangoSettings(apiUrl, extension, line);
    }
    String publicUrl = text(request.publicUrl()).replaceAll("/$", "");
    String relayUrl = text(request.relayUrl()).replaceAll("/$", "");
    String webhookBaseUrl = text(request.webhookBaseUrl()).replaceAll("/+$", "");
    requireUrl(publicUrl, false, "INTEGRATION_PUBLIC_URL_INVALID");
    requireUrl(relayUrl, false, "INTEGRATION_RELAY_URL_INVALID");
    requireUrl(webhookBaseUrl, false, "INTEGRATION_WEBHOOK_BASE_INVALID");
    return new MessengerSettings(
        apiUrl,
        publicUrl,
        relayUrl,
        webhookBaseUrl,
        request.reminderButtons() == null || request.reminderButtons());
  }

  private static Object secrets(
      String type, IntegrationSecretRequest request, MessengerSecrets previous) {
    if (MANGO.equals(type)) {
      String key = text(request.apiKey());
      String salt = text(request.apiSalt());
      if (key.isEmpty() || salt.isEmpty()) {
        throw badRequest("INTEGRATION_SECRET_INVALID", "Mango API key and salt are required");
      }
      return new MangoSecrets(key, salt);
    }
    String token = text(request.botToken());
    String relaySecret = text(request.relaySecret());
    String webhookSecret = text(request.webhookSecret());
    if (previous != null) {
      if (token.isEmpty()) token = text(previous.botToken());
      if (relaySecret.isEmpty()) relaySecret = text(previous.relaySecret());
      if (webhookSecret.isEmpty()) webhookSecret = text(previous.webhookSecret());
    }
    if (token.isEmpty() && relaySecret.isEmpty()) {
      throw badRequest("INTEGRATION_SECRET_INVALID", "Bot token or relay secret is required");
    }
    if (!webhookSecret.isEmpty() && !webhookSecret.matches("^[A-Za-z0-9_-]{5,256}$")) {
      throw badRequest(
          "INTEGRATION_WEBHOOK_SECRET_INVALID",
          "Webhook secret must contain 5-256 letters, digits, underscores or hyphens");
    }
    return new MessengerSecrets(token, relaySecret, webhookSecret);
  }

  private static Object defaultSettings(String type) {
    return switch (type) {
      case MANGO -> new MangoSettings(DEFAULT_MANGO_API_URL, "", "");
      case TELEGRAM -> new MessengerSettings(DEFAULT_TELEGRAM_API_URL, "", "", "", true);
      case MAX -> new MessengerSettings(DEFAULT_MAX_API_URL, "", "", "", true);
      default -> throw new IllegalArgumentException("Unsupported integration");
    };
  }

  private static String text(String value) {
    return value == null ? "" : value.trim();
  }

  private static void requireUrl(String value, boolean required, String code) {
    if (value.isEmpty() && !required) return;
    try {
      URI uri = URI.create(value);
      if (!List.of("http", "https").contains(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null) {
        throw new IllegalArgumentException("Invalid URL");
      }
    } catch (IllegalArgumentException ex) {
      throw badRequest(code, "Integration URL must be a valid HTTP(S) URL");
    }
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
