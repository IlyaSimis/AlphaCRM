package ru.quietclinic.tenancy;

import org.springframework.stereotype.Component;
import ru.quietclinic.platform.TenantRunner;

/** Прогоняет фоновую операцию по каждой активной базе; legacy-режим выполняет её один раз. */
@Component
public class TenantExecutor implements TenantRunner {
  private final TenantDirectory directory;

  TenantExecutor(TenantDirectory directory) {
    this.directory = directory;
  }

  public void forEachTenant(Runnable action) {
    if (!directory.enabled()) {
      action.run();
      return;
    }
    directory.activeTenants().forEach(tenant -> TenantScope.run(tenant.id(), action));
  }
}
