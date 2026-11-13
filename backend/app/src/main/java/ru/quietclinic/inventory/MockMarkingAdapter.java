package ru.quietclinic.inventory;

import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Безопасная заглушка MVP: отвечает «принято» и ставит ссылку оператора {@code mock:<id>}. Ничего
 * никуда не отправляет. Включается {@code clinic.marking.adapter=mock}.
 */
@Component
@ConditionalOnProperty(name = "clinic.marking.adapter", havingValue = "mock")
class MockMarkingAdapter implements MarkingPort {
  @Override
  public String adapter() {
    return "MOCK";
  }

  private static Result ok(UUID operationId) {
    return Result.ok("mock:" + operationId);
  }

  @Override
  public Result accept(UUID operationId, Set<String> sgtins) {
    return ok(operationId);
  }

  @Override
  public Result withdrawForMedicalUse(UUID operationId, Set<String> sgtins) {
    return ok(operationId);
  }

  @Override
  public Result writeOff(UUID operationId, Set<String> sgtins) {
    return ok(operationId);
  }

  @Override
  public Result returnToSupplier(UUID operationId, Set<String> sgtins) {
    return ok(operationId);
  }
}
