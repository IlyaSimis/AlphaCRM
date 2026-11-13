package ru.quietclinic.integrations;

import static ru.quietclinic.platform.PlatformConfig.badRequest;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Регистрирует webhook от имени клиники и сверяет фактическую подписку у провайдера. */
@Service
class MessengerWebhookRegistrationService {
  private final MessengerIntegrationClientFactory integrations;
  private final MessengerProviderHttp http;
  private final JsonMapper json;

  MessengerWebhookRegistrationService(
      MessengerIntegrationClientFactory integrations, MessengerProviderHttp http, JsonMapper json) {
    this.integrations = integrations;
    this.http = http;
    this.json = json;
  }

  IntegrationWebhookStatusView connect(String rawType) {
    var configuration = configuration(rawType);
    try {
      if (IntegrationService.TELEGRAM.equals(configuration.type())) {
        var response =
            http.postJson(
                URI.create(api(configuration) + "/setWebhook"),
                json.writeValueAsString(
                    Map.of(
                        "url", configuration.url(),
                        "secret_token", configuration.secret(),
                        "allowed_updates", List.of("message", "callback_query"))),
                Map.of());
        requireAccepted(response, true);
      } else {
        var response =
            http.postJson(
                URI.create(api(configuration) + "/subscriptions"),
                json.writeValueAsString(
                    Map.of(
                        "url", configuration.url(),
                        "secret", configuration.secret(),
                        "update_types",
                            List.of("message_created", "bot_started", "message_callback"))),
                Map.of("Authorization", configuration.token()));
        requireAccepted(response, false);
      }
    } catch (IOException | IllegalArgumentException error) {
      throw conflict("INTEGRATION_PROVIDER_UNAVAILABLE", "Messenger API is unavailable");
    }
    return status(rawType);
  }

  IntegrationWebhookStatusView status(String rawType) {
    var configuration = configuration(rawType);
    try {
      if (IntegrationService.TELEGRAM.equals(configuration.type())) {
        var response =
            http.postJson(URI.create(api(configuration) + "/getWebhookInfo"), "{}", Map.of());
        var body = requireAccepted(response, true);
        var result = body.path("result");
        var registeredUrl = result.path("url").asText("");
        var lastError = result.path("last_error_message").asText("");
        var state =
            !configuration.url().equals(registeredUrl)
                ? "NOT_CONFIGURED"
                : lastError.isEmpty() ? "CONNECTED" : "ERROR";
        return new IntegrationWebhookStatusView(
            state,
            configuration.url(),
            lastError.isEmpty() ? null : lastError,
            result.path("pending_update_count").asInt(0));
      }
      var response =
          http.get(
              URI.create(api(configuration) + "/subscriptions"),
              Map.of("Authorization", configuration.token()));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw conflict("INTEGRATION_PROVIDER_REJECTED", "Messenger rejected webhook status");
      }
      var registered = false;
      for (var subscription : json.readTree(response.body()).path("subscriptions")) {
        if (configuration.url().equals(subscription.path("url").asText(""))) registered = true;
      }
      return new IntegrationWebhookStatusView(
          registered ? "CONNECTED" : "NOT_CONFIGURED", configuration.url(), null, null);
    } catch (IOException | IllegalArgumentException error) {
      throw conflict("INTEGRATION_PROVIDER_UNAVAILABLE", "Messenger API is unavailable");
    }
  }

  private JsonNode requireAccepted(
      MessengerProviderHttp.ProviderResponse response, boolean telegram) {
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw conflict("INTEGRATION_PROVIDER_REJECTED", "Messenger rejected webhook configuration");
    }
    var body = json.readTree(response.body());
    if (!body.path(telegram ? "ok" : "success").asBoolean(false)) {
      throw conflict("INTEGRATION_PROVIDER_REJECTED", "Messenger rejected webhook configuration");
    }
    return body;
  }

  private ProviderConfiguration configuration(String rawType) {
    var type = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT);
    if (!List.of(IntegrationService.TELEGRAM, IntegrationService.MAX).contains(type)) {
      throw badRequest(
          "INTEGRATION_TYPE_UNSUPPORTED", "Automatic webhook supports Telegram and MAX only");
    }
    var integration =
        integrations
            .forClinic(clinicId(), type)
            .orElseThrow(() -> notFound("INTEGRATION_NOT_FOUND", "Integration is not configured"));
    if (!integration.active()) {
      throw conflict("INTEGRATION_NOT_ACTIVE", "Activate integration before connecting webhook");
    }
    var settings = integration.settings();
    var secrets = integration.secrets();
    if (secrets == null || text(secrets.botToken()).isEmpty()) {
      throw conflict("INTEGRATION_SECRET_MISSING", "Bot token is required");
    }
    if (text(secrets.webhookSecret()).isEmpty()) {
      throw conflict("INTEGRATION_WEBHOOK_SECRET_MISSING", "Webhook secret is required");
    }
    if (IntegrationService.TELEGRAM.equals(type) && !text(settings.relayUrl()).isEmpty()) {
      throw conflict(
          "INTEGRATION_RELAY_WEBHOOK_EXTERNAL",
          "Telegram relay webhook must be configured on the relay");
    }
    var base = publicBase(settings.webhookBaseUrl(), type);
    var url =
        base
            + "/api/v1/integrations/"
            + type.toLowerCase(Locale.ROOT)
            + "/events/"
            + integration.publicKey();
    return new ProviderConfiguration(
        type,
        text(settings.apiUrl()).replaceAll("/$", ""),
        text(secrets.botToken()),
        text(secrets.webhookSecret()),
        url);
  }

  private static String publicBase(String rawBase, String type) {
    try {
      var base = text(rawBase).replaceAll("/+$", "");
      var uri = URI.create(base);
      var port = uri.getPort();
      if (!"https".equals(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || !text(uri.getQuery()).isEmpty()
          || !text(uri.getFragment()).isEmpty()) {
        throw new IllegalArgumentException("Invalid public URL");
      }
      if (IntegrationService.MAX.equals(type) && port != -1 && port != 443) {
        throw new IllegalArgumentException("MAX requires HTTPS port 443");
      }
      if (IntegrationService.TELEGRAM.equals(type)
          && port != -1
          && !List.of(80, 88, 443, 8443).contains(port)) {
        throw new IllegalArgumentException("Unsupported Telegram webhook port");
      }
      return base;
    } catch (IllegalArgumentException error) {
      throw badRequest(
          "INTEGRATION_WEBHOOK_BASE_INVALID",
          "Public CRM URL must be a valid HTTPS URL on a messenger-supported port");
    }
  }

  private static String api(ProviderConfiguration configuration) {
    return IntegrationService.TELEGRAM.equals(configuration.type())
        ? configuration.apiUrl() + "/bot" + configuration.token()
        : configuration.apiUrl();
  }

  private static String text(String value) {
    return value == null ? "" : value.trim();
  }

  private record ProviderConfiguration(
      String type, String apiUrl, String token, String secret, String url) {}
}
