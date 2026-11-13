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
  private final TenantRunner tenants;

  DomainEventScheduler(DomainEventRelay relay, TenantRunner tenants) {
    this.relay = relay;
    this.tenants = tenants;
  }

  @Scheduled(
      initialDelayString = "${clinic.events.poll-interval:PT30S}",
      fixedDelayString = "${clinic.events.poll-interval:PT30S}")
  void retry() {
    tenants.forEachTenant(() -> relay.runOnce(Instant.now()));
  }
}
