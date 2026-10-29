package ru.quietclinic.telephony;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Подписанный callback Mango: сначала звонит добавочный сотрудника, затем соединяет с пациентом.
 */
@Component
class MangoGateway {
  private final JsonMapper json;
  private final HttpClient http;
  private final String apiUrl;
  private final String key;
  private final String salt;
  private final String defaultExtension;
  private final String lineNumber;

  MangoGateway(
      JsonMapper json,
      @Value("${clinic.telephony.mango.api-url:https://app.mango-office.ru}") String apiUrl,
      @Value("${clinic.telephony.mango.api-key:}") String key,
      @Value("${clinic.telephony.mango.api-salt:}") String salt,
      @Value("${clinic.telephony.mango.default-extension:}") String defaultExtension,
      @Value("${clinic.telephony.mango.line-number:}") String lineNumber) {
    this.json = json;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    this.apiUrl = apiUrl.replaceAll("/$", "");
    this.key = key == null ? "" : key.trim();
    this.salt = salt == null ? "" : salt.trim();
    this.defaultExtension = defaultExtension == null ? "" : defaultExtension.trim();
    this.lineNumber = lineNumber == null ? "" : lineNumber.trim();
  }

  boolean configured() {
    return !key.isEmpty() && !salt.isEmpty();
  }

  String defaultExtension() {
    return defaultExtension;
  }

  void callback(String commandId, String extension, String phone) {
    if (!configured())
      throw conflict("TELEPHONY_NOT_CONFIGURED", "Mango integration is not configured");
    var from = new LinkedHashMap<String, Object>();
    from.put("extension", extension);
    var payload = new LinkedHashMap<String, Object>();
    payload.put("command_id", commandId);
    payload.put("from", from);
    payload.put("to_number", phone);
    if (!lineNumber.isEmpty()) payload.put("line_number", lineNumber);
    var body = json.writeValueAsString(payload);
    var form =
        Map.of(
            "vpbx_api_key", key,
            "sign", MangoSignature.sign(key, body, salt),
            "json", body);
    var encoded =
        form.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));
    var request =
        HttpRequest.newBuilder(URI.create(apiUrl + "/vpbx/commands/callback"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(encoded, StandardCharsets.UTF_8))
            .build();
    try {
      var response = http.send(request, HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw conflict("MANGO_REQUEST_REJECTED", "Mango rejected the call request");
      }
    } catch (IOException error) {
      throw conflict("MANGO_UNAVAILABLE", "Mango is unavailable");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw conflict("MANGO_UNAVAILABLE", "Mango is unavailable");
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
