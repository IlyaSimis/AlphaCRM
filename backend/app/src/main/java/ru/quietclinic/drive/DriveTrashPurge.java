package ru.quietclinic.drive;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Раз в час удаляет из корзины то, что пролежало там дольше clinic.drive.trash-days. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "clinic.drive.purge-enabled",
    havingValue = "true",
    matchIfMissing = true)
class DriveTrashPurge {
  private final DriveService drive;
  private final ru.quietclinic.tenancy.TenantExecutor tenants;
  private final int trashDays;

  DriveTrashPurge(
      DriveService drive,
      ru.quietclinic.tenancy.TenantExecutor tenants,
      @Value("${clinic.drive.trash-days:30}") int trashDays) {
    this.drive = drive;
    this.tenants = tenants;
    this.trashDays = trashDays;
  }

  @Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
  void purge() {
    tenants.forEachTenant(() -> drive.purge(Instant.now().minus(Duration.ofDays(trashDays))));
  }
}
