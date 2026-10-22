package ru.quietclinic.platform;

import org.springframework.security.core.context.SecurityContextHolder;

public final class PlatformConfig {
  private PlatformConfig() {}

  public static void requireRole(String... roles) {
    var allowed = java.util.Arrays.stream(roles).anyMatch(PlatformConfig::hasRole);
    if (!allowed) throw DomainException.forbidden("Недостаточно прав для операции");
  }

  public static boolean hasRole(String role) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null
        && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
  }

  public static java.util.UUID actorId() {
    return java.util.UUID.fromString(
        SecurityContextHolder.getContext().getAuthentication().getName());
  }

  public static RuntimeException conflict(String code, String message) {
    return DomainException.conflict(code, message);
  }

  public static RuntimeException forbidden(String message) {
    return DomainException.forbidden(message);
  }

  public static RuntimeException notFound(String message) {
    return DomainException.notFound(message);
  }
}
