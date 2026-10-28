package ru.quietclinic.inventory;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Повтор операций маркировки по расписанию; в тестах выключен (clinic.marking.relay-enabled). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "clinic.marking.relay-enabled",
    havingValue = "true",
    matchIfMissing = true)
class MarkingScheduler {
  private final MarkingRelay relay;

  MarkingScheduler(MarkingRelay relay) {
    this.relay = relay;
  }

  @Scheduled(
      initialDelayString = "${clinic.marking.poll-interval:PT60S}",
      fixedDelayString = "${clinic.marking.poll-interval:PT60S}")
  void run() {
    relay.runOnce(Instant.now());
  }
}
