package ru.quietclinic.assistant;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.quietclinic.assistant.AssistantController.Destination;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** OpenAI-compatible chat completions: смена провайдера — только настройки backend. */
@Component
class OpenAiCompatibleAssistantGateway implements AssistantGateway {
  private final JsonMapper json;
  private final HttpClient http;
  private final String apiKey;
  private final String model;
  private final String apiUrl;

  OpenAiCompatibleAssistantGateway(
      JsonMapper json,
      @Value("${clinic.assistant.api-key:}") String apiKey,
      @Value("${clinic.assistant.model:gpt-5.4-nano}") String model,
      @Value("${clinic.assistant.api-url:https://api.openai.com/v1}") String apiUrl) {
    this.json = json;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.apiKey = apiKey;
    this.model = model;
    this.apiUrl = apiUrl.replaceAll("/$", "");
  }

  @Override
  public boolean enabled() {
    return !apiKey.isBlank();
  }

  @Override
  public String model() {
    return model;
  }

  @Override
  public GatewayResult navigate(String query, String currentPage, List<Destination> destinations) {
    if (!enabled())
      throw conflict("ASSISTANT_NOT_CONFIGURED", "AI navigation provider is not configured");
    var system =
        "Ты навигатор AlphaCRM. Выбери ровно один destinationId из каталога по запросу на русском."
            + " Ничего не создавай и не изменяй. Не выдумывай страницы. Ответь только JSON"
            + " {\"destinationId\":\"id\"}. Если подходят специальные представления задач,"
            + " выбирай их перед общей страницей. Текущая страница: "
            + currentPage;
    var user = Map.of("query", query, "destinations", destinations);
    var body =
        Map.of(
            "model",
            model,
            "max_completion_tokens",
            160,
            "response_format",
            Map.of("type", "json_object"),
            "messages",
            List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", json.writeValueAsString(user))));
    var request =
        HttpRequest.newBuilder(URI.create(apiUrl + "/chat/completions"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build();
    try {
      var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw conflict("ASSISTANT_PROVIDER_ERROR", "AI provider rejected the request");
      }
      var root = json.readValue(response.body(), JsonNode.class);
      var content = root.path("choices").path(0).path("message").required("content").asText();
      var answer = json.readValue(content, JsonNode.class);
      var usage = root.path("usage");
      return new GatewayResult(
          answer.required("destinationId").asText(),
          usage.path("prompt_tokens").asLong(0),
          usage.path("completion_tokens").asLong(0));
    } catch (IOException | InterruptedException error) {
      if (error instanceof InterruptedException) Thread.currentThread().interrupt();
      throw conflict("ASSISTANT_PROVIDER_UNAVAILABLE", "AI provider is unavailable");
    } catch (RuntimeException error) {
      if (error instanceof IllegalArgumentException) {
        throw conflict("ASSISTANT_INVALID_RESPONSE", "AI provider returned an invalid response");
      }
      throw error;
    }
  }
}
