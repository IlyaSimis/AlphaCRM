package ru.quietclinic.communications;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** POST JSON наружу. Ни адрес (в нём может быть токен), ни тело здесь не логируются. */
@Component
class HttpTransport {
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  /** HTTP-статус ответа; сетевая ошибка — IOException. */
  int postJson(URI uri, String body, Map<String, String> headers) throws IOException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
    headers.forEach(request::header);
    try {
      return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted", ex);
    }
  }
}
