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
  private final ru.quietclinic.tenancy.TenantExecutor tenants;

  MarkingScheduler(MarkingRelay relay, ru.quietclinic.tenancy.TenantExecutor tenants) {
    this.relay = relay;
    this.tenants = tenants;
  }

  @Scheduled(
      initialDelayString = "${clinic.marking.poll-interval:PT60S}",
      fixedDelayString = "${clinic.marking.poll-interval:PT60S}")
  void run() {
    tenants.forEachTenant(() -> relay.runOnce(Instant.now()));
  }
}
