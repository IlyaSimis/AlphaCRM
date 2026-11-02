package ru.quietclinic.tenancy;

import java.util.UUID;
import java.util.function.Supplier;

/** Серверный tenant-контекст для входа, provisioning и фоновых задач. */
public final class TenantScope {
  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  private TenantScope() {}

  public static UUID current() {
    return CURRENT.get();
  }

  public static <T> T call(UUID clinicId, Supplier<T> action) {
    UUID previous = CURRENT.get();
    CURRENT.set(clinicId);
    try {
      return action.get();
    } finally {
      restore(previous);
    }
  }

  public static void run(UUID clinicId, Runnable action) {
    call(
        clinicId,
        () -> {
          action.run();
          return null;
        });
  }

  private static void restore(UUID previous) {
    if (previous == null) CURRENT.remove();
    else CURRENT.set(previous);
  }
}
