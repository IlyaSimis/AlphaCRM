package ru.quietclinic.inventory;

import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Интеграция не настроена: операции остаются PENDING с причиной {@code MARKING_NOT_CONFIGURED},
 * остатки и проведение накладных работают. Экран «Маркировка» показывает очередь.
 */
@Configuration
class UnconfiguredMarkingAdapter {
  static final String NOT_CONFIGURED = "MARKING_NOT_CONFIGURED";

  @Bean
  @ConditionalOnMissingBean(MarkingPort.class)
  MarkingPort unconfiguredMarkingPort() {
    return new MarkingPort() {
      @Override
      public String adapter() {
        return "NONE";
      }

      @Override
      public Result accept(UUID operationId, Set<String> sgtins) {
        return Result.later(NOT_CONFIGURED);
      }

      @Override
      public Result withdrawForMedicalUse(UUID operationId, Set<String> sgtins) {
        return Result.later(NOT_CONFIGURED);
      }

      @Override
      public Result writeOff(UUID operationId, Set<String> sgtins) {
        return Result.later(NOT_CONFIGURED);
      }

      @Override
      public Result returnToSupplier(UUID operationId, Set<String> sgtins) {
        return Result.later(NOT_CONFIGURED);
      }
    };
  }
}
