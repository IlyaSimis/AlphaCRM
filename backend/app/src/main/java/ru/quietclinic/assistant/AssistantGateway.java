package ru.quietclinic.assistant;

import java.util.List;
import ru.quietclinic.assistant.AssistantController.Destination;

/** Порт модели: возвращает только id из переданного каталога и учёт токенов. */
public interface AssistantGateway {
  boolean enabled();

  String model();

  GatewayResult navigate(String query, String currentPage, List<Destination> destinations);

  record GatewayResult(String destinationId, long inputTokens, long outputTokens) {}
}
