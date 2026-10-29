package ru.quietclinic.commercial;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** ЮKassa hosted checkout. Подтверждение вебхука проверяется отдельным GET к API провайдера. */
@Component
class YooKassaCommercialGateway implements CommercialPaymentGateway {
  private final JsonMapper json;
  private final HttpClient http;
  private final String shopId;
  private final String secretKey;
  private final String returnUrl;
  private final String apiUrl;

  YooKassaCommercialGateway(
      JsonMapper json,
      @Value("${clinic.commercial.yookassa.shop-id:}") String shopId,
      @Value("${clinic.commercial.yookassa.secret-key:}") String secretKey,
      @Value("${clinic.commercial.yookassa.return-url:}") String returnUrl,
      @Value("${clinic.commercial.yookassa.api-url:https://api.yookassa.ru/v3}") String apiUrl) {
    this.json = json;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.shopId = shopId;
    this.secretKey = secretKey;
    this.returnUrl = returnUrl;
    this.apiUrl = apiUrl.replaceAll("/$", "");
  }

  @Override
  public boolean enabled() {
    return !shopId.isBlank() && !secretKey.isBlank() && !returnUrl.isBlank();
  }

  @Override
  public ProviderCheckout createCheckout(PaymentCommand command) {
    requireEnabled();
    var body =
        Map.of(
            "amount", amount(command.amountMinor(), command.currency()),
            "capture", true,
            "confirmation", Map.of("type", "redirect", "return_url", returnUrl),
            "description", "AlphaCRM: тариф " + command.planName(),
            "metadata",
                Map.of(
                    "paymentId",
                    command.paymentId().toString(),
                    "clinicId",
                    command.clinicId().toString()));
    var request =
        authorized(apiUrl + "/payments")
            .header("Idempotence-Key", command.paymentId().toString())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
    var response = send(request);
    return new ProviderCheckout(
        response.required("id").asText(),
        response.path("confirmation").required("confirmation_url").asText());
  }

  @Override
  public ProviderPayment getPayment(String providerRef) {
    requireEnabled();
    var response = send(authorized(apiUrl + "/payments/" + providerRef).GET().build());
    var value = new BigDecimal(response.path("amount").path("value").asText("0"));
    return new ProviderPayment(
        response.required("id").asText(),
        response.required("status").asText(),
        response.path("paid").asBoolean(false),
        value.multiply(BigDecimal.valueOf(100)).longValue(),
        response.path("amount").path("currency").asText(),
        UUID.fromString(response.path("metadata").required("paymentId").asText()));
  }

  private JsonNode send(HttpRequest request) {
    try {
      var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw conflict("PAYMENT_PROVIDER_ERROR", "Payment provider rejected the request");
      }
      return json.readValue(response.body(), JsonNode.class);
    } catch (java.io.IOException | InterruptedException error) {
      if (error instanceof InterruptedException) Thread.currentThread().interrupt();
      throw conflict("PAYMENT_PROVIDER_UNAVAILABLE", "Payment provider is unavailable");
    }
  }

  private HttpRequest.Builder authorized(String url) {
    var credentials =
        Base64.getEncoder()
            .encodeToString((shopId + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
    return HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(20))
        .header("Authorization", "Basic " + credentials);
  }

  private void requireEnabled() {
    if (!enabled()) {
      throw conflict(
          "PAYMENT_PROVIDER_NOT_CONFIGURED", "Subscription payment provider is not configured");
    }
  }

  private static Map<String, String> amount(long minor, String currency) {
    return Map.of(
        "value",
        BigDecimal.valueOf(minor)
            .divide(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.UNNECESSARY)
            .toPlainString(),
        "currency",
        currency.toUpperCase(Locale.ROOT));
  }
}
