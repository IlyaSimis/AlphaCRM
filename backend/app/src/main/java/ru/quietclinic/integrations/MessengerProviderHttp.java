package ru.quietclinic.integrations;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** HTTP к Bot API. URL с Telegram token и тела запросов здесь не логируются. */
@Component
class MessengerProviderHttp {
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  ProviderResponse postJson(URI uri, String body, Map<String, String> headers) throws IOException {
    var request =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    headers.forEach(request::header);
    return execute(request.build());
  }

  ProviderResponse get(URI uri, Map<String, String> headers) throws IOException {
    var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET();
    headers.forEach(request::header);
    return execute(request.build());
  }

  private ProviderResponse execute(HttpRequest request) throws IOException {
    try {
      var response =
          client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return new ProviderResponse(response.statusCode(), response.body());
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted", error);
    }
  }

  record ProviderResponse(int statusCode, String body) {}
}
