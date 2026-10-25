package ru.quietclinic.communications;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Запуск доставщика по расписанию; в тестах выключен (clinic.outbox.enabled=false). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "clinic.outbox.enabled", havingValue = "true", matchIfMissing = true)
class OutboxScheduler {
  private final OutboxDispatcher dispatcher;

  OutboxScheduler(OutboxDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Scheduled(
      initialDelayString = "${clinic.outbox.poll-interval:PT30S}",
      fixedDelayString = "${clinic.outbox.poll-interval:PT30S}")
  void deliver() {
    dispatcher.runOnce(Instant.now());
  }
}
