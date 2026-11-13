package ru.quietclinic.platform;

/** Порт фонового запуска по клиникам; platform не зависит от реализации tenancy. */
public interface TenantRunner {
  void forEachTenant(Runnable action);
}
