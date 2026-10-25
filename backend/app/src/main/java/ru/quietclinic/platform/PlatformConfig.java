package ru.quietclinic.platform;

import org.springframework.security.core.context.SecurityContextHolder;

public final class PlatformConfig {
  private PlatformConfig() {}

  public static void requireRole(String... roles) {
    var allowed = java.util.Arrays.stream(roles).anyMatch(PlatformConfig::hasRole);
    if (!allowed) throw DomainException.forbidden("ACCESS_DENIED", "Not allowed for this role");
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

  /** Клиника сотрудника из сессии. Без входа — 401: данные чужой клиники не отдаются никогда. */
  public static java.util.UUID clinicId() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof ClinicMember member) {
      return member.clinicId();
    }
    throw DomainException.unauthorized("Sign-in required");
  }

  public static RuntimeException conflict(String code, String message) {
    return DomainException.conflict(code, message);
  }

  public static RuntimeException forbidden(String code, String message) {
    return DomainException.forbidden(code, message);
  }

  public static RuntimeException unauthorized(String message) {
    return DomainException.unauthorized(message);
  }

  public static RuntimeException unauthorized(String code, String message) {
    return DomainException.unauthorized(code, message);
  }

  public static RuntimeException tooManyRequests(String code, String message) {
    return DomainException.tooManyRequests(code, message);
  }

  public static RuntimeException notFound(String code, String message) {
    return DomainException.notFound(code, message);
  }
}
