package ru.quietclinic.integrations;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;

/** HTTP client одной активной Mango-интеграции. */
final class ScopedMangoClient implements MangoClient {
  private final JsonMapper json;
  private final HttpClient http;
  private final MangoSettings settings;
  private final MangoSecrets secrets;
  private final boolean active;

  ScopedMangoClient(JsonMapper json, MangoSettings settings, MangoSecrets secrets, boolean active) {
    this.json = json;
    this.settings = settings;
    this.secrets = secrets;
    this.active = active;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  @Override
  public boolean configured() {
    return active && secrets != null && !secrets.apiKey().isBlank() && !secrets.apiSalt().isBlank();
  }

  @Override
  public String defaultExtension() {
    return settings.defaultExtension();
  }

  @Override
  public void callback(String commandId, String extension, String phone) {
    if (!configured()) {
      throw conflict("TELEPHONY_NOT_CONFIGURED", "Mango integration is not configured");
    }
    var from = new LinkedHashMap<String, Object>();
    from.put("extension", extension);
    var payload = new LinkedHashMap<String, Object>();
    payload.put("command_id", commandId);
    payload.put("from", from);
    payload.put("to_number", phone);
    if (!settings.lineNumber().isEmpty()) payload.put("line_number", settings.lineNumber());
    var body = json.writeValueAsString(payload);
    var form =
        Map.of(
            "vpbx_api_key", secrets.apiKey(),
            "sign", sign(secrets.apiKey(), body, secrets.apiSalt()),
            "json", body);
    var encoded =
        form.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));
    var request =
        HttpRequest.newBuilder(URI.create(settings.apiUrl() + "/vpbx/commands/callback"))
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

  private static String sign(String key, String body, String salt) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest((key + body + salt).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
