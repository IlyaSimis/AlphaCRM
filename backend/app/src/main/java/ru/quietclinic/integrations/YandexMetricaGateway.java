package ru.quietclinic.integrations;

import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import ru.quietclinic.insights.WebAnalyticsPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reporting API Метрики: только суммарные показатели, дни и источники трафика. */
@Component
class YandexMetricaGateway implements WebAnalyticsPort {
  private static final String TRAFFIC_METRICS =
      "ym:s:visits,ym:s:users,ym:s:pageviews,ym:s:percentNewVisitors,ym:s:bounceRate,"
          + "ym:s:pageDepth,ym:s:avgVisitDurationSeconds,ym:s:anyGoalReaches,"
          + "ym:s:anyGoalConversionRate";
  private static final String SOURCE_METRICS =
      "ym:s:visits,ym:s:users,ym:s:anyGoalReaches";

  private final YandexMetricaClientFactory connections;
  private final JsonMapper json;
  private final HttpClient http;

  YandexMetricaGateway(YandexMetricaClientFactory connections, JsonMapper json) {
    this.connections = connections;
    this.json = json;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  }

  @Override
  public Report load(UUID clinicId, LocalDate from, LocalDate to) {
    YandexMetricaClientFactory.Connection connection = connections.forClinic(clinicId).orElse(null);
    if (connection == null) return Report.unavailable("NOT_CONFIGURED");
    if (!"ACTIVE".equals(connection.status())) return Report.unavailable("DISABLED");
    try {
      JsonNode daily =
          request(
              reportUri(connection, from, to)
                  .queryParam("dimensions", "ym:s:date")
                  .queryParam("sort", "ym:s:date")
                  .queryParam("limit", 10000)
                  .build()
                  .toUri(),
              connection.oauthToken());
      JsonNode sources =
          request(
              reportUri(connection, from, to)
                  .replaceQueryParam("metrics", SOURCE_METRICS)
                  .queryParam("dimensions", "ym:s:lastSignTrafficSource")
                  .queryParam("sort", "-ym:s:visits")
                  .queryParam("limit", 10)
                  .build()
                  .toUri(),
              connection.oauthToken());
      return new Report("READY", null, traffic(daily.path("totals")), days(daily), sources(sources));
    } catch (ProviderException ex) {
      return Report.error(ex.getMessage());
    } catch (IOException | RuntimeException ex) {
      return Report.error("YANDEX_METRICA_UNAVAILABLE");
    }
  }

  /** Явная активация проверяет и токен, и доступ именно к указанному счётчику. */
  void requireValid(IntegrationRecord integration) {
    connections.requireValid(integration);
    YandexMetricaClientFactory.Connection connection =
        connections
            .forClinic(integration.clinicId())
            .orElseThrow(() -> notFound("INTEGRATION_NOT_FOUND", "Integration is not configured"));
    URI uri =
        UriComponentsBuilder.fromUriString(connection.apiUrl())
            .path("/management/v1/counter/{counterId}")
            .buildAndExpand(connection.counterId())
            .encode()
            .toUri();
    try {
      Response response = get(uri, connection.oauthToken());
      if (response.status() < 200 || response.status() >= 300) {
        throw conflict(
            "YANDEX_METRICA_ACCESS_FAILED", "Yandex Metrica rejected counter access");
      }
    } catch (IOException ex) {
      throw conflict("YANDEX_METRICA_UNAVAILABLE", "Yandex Metrica is unavailable");
    }
  }

  private UriComponentsBuilder reportUri(
      YandexMetricaClientFactory.Connection connection, LocalDate from, LocalDate to) {
    return UriComponentsBuilder.fromUriString(connection.apiUrl())
        .path("/stat/v1/data")
        .queryParam("ids", connection.counterId())
        .queryParam("metrics", TRAFFIC_METRICS)
        .queryParam("date1", from)
        .queryParam("date2", to);
  }

  private JsonNode request(URI uri, String token) throws IOException {
    Response response = get(uri, token);
    if (response.status() < 200 || response.status() >= 300) {
      throw new ProviderException("HTTP_" + response.status());
    }
    return json.readTree(response.body());
  }

  private Response get(URI uri, String token) throws IOException {
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "OAuth " + token)
            .header("Accept", "application/json")
            .GET()
            .build();
    try {
      HttpResponse<String> response =
          http.send(request, HttpResponse.BodyHandlers.ofString());
      return new Response(response.statusCode(), response.body());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted", ex);
    }
  }

  private static WebAnalyticsPort.Traffic traffic(JsonNode node) {
    JsonNode values = values(node);
    return new WebAnalyticsPort.Traffic(
        number(values, 0),
        number(values, 1),
        number(values, 2),
        decimal(values, 3),
        decimal(values, 4),
        decimal(values, 5),
        decimal(values, 6),
        number(values, 7),
        decimal(values, 8));
  }

  private static List<WebAnalyticsPort.DailyPoint> days(JsonNode root) {
    List<WebAnalyticsPort.DailyPoint> result = new ArrayList<>();
    for (JsonNode row : root.path("data")) {
      JsonNode values = values(row.path("metrics"));
      String date = row.path("dimensions").path(0).path("name").asText("");
      if (!date.isBlank()) {
        result.add(
            new WebAnalyticsPort.DailyPoint(
                LocalDate.parse(date), number(values, 0), number(values, 1), number(values, 7)));
      }
    }
    return result;
  }

  private static List<WebAnalyticsPort.TrafficSource> sources(JsonNode root) {
    List<WebAnalyticsPort.TrafficSource> result = new ArrayList<>();
    for (JsonNode row : root.path("data")) {
      JsonNode values = values(row.path("metrics"));
      String source = row.path("dimensions").path(0).path("name").asText("Не определён");
      result.add(
          new WebAnalyticsPort.TrafficSource(
              source, number(values, 0), number(values, 1), number(values, 2)));
    }
    return result;
  }

  /** API возвращает totals и metrics либо массивом чисел, либо вложенным массивом. */
  private static JsonNode values(JsonNode node) {
    if (node.isArray() && node.size() == 1 && node.path(0).isArray()) return node.path(0);
    return node;
  }

  private static long number(JsonNode values, int index) {
    return values.path(index).asLong(0);
  }

  private static double decimal(JsonNode values, int index) {
    return values.path(index).asDouble(0);
  }

  private record Response(int status, String body) {}

  private static final class ProviderException extends RuntimeException {
    private ProviderException(String message) {
      super(message);
    }
  }
}
