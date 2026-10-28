package ru.quietclinic.platform;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Фоновый повтор событий, которые не раздались сразу после фиксации транзакции. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "clinic.events.enabled", havingValue = "true", matchIfMissing = true)
class DomainEventScheduler {
  private final DomainEventRelay relay;

  DomainEventScheduler(DomainEventRelay relay) {
    this.relay = relay;
  }

  @Scheduled(
      initialDelayString = "${clinic.events.poll-interval:PT30S}",
      fixedDelayString = "${clinic.events.poll-interval:PT30S}")
  void retry() {
    relay.runOnce(Instant.now());
  }
}
