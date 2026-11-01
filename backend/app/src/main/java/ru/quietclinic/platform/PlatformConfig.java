package ru.quietclinic.platform;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class PlatformConfig {
  private PlatformConfig() {}

  public static void requireRole(String... roles) {
    boolean allowed = Arrays.stream(roles).anyMatch(PlatformConfig::hasRole);
    if (!allowed) throw DomainException.forbidden("ACCESS_DENIED", "Not allowed for this role");
  }

  public static boolean hasRole(String role) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null
        && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
  }

  /** Роли сотрудника из сессии (без префикса ROLE_). */
  public static Set<String> currentRoles() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return Set.of();
    return auth.getAuthorities().stream()
        .map(a -> a.getAuthority())
        .filter(a -> a.startsWith("ROLE_"))
        .map(a -> a.substring("ROLE_".length()))
        .collect(Collectors.toUnmodifiableSet());
  }

  /** Разрешения сотрудника из сессии — объединение разрешений его ролей. */
  public static Set<Permission> currentPermissions() {
    return RolePermissions.of(currentRoles());
  }

  /** Есть ли у сотрудника хотя бы одно из разрешений. */
  public static boolean can(Permission... permissions) {
    Set<Permission> granted = currentPermissions();
    return Arrays.stream(permissions).anyMatch(granted::contains);
  }

  /**
   * Проверка разрешения на сервере: хотя бы одно из перечисленных, иначе {@code 403 ACCESS_DENIED}.
   * Атрибуты (своя клиника, своя запись, связь с лечением) сервис проверяет после.
   */
  public static void requirePermission(Permission... permissions) {
    if (!can(permissions))
      throw DomainException.forbidden("ACCESS_DENIED", "Not allowed for this role");
  }

  public static UUID actorId() {
    return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
  }

  /** Клиника сотрудника из сессии. Без входа — 401: данные чужой клиники не отдаются никогда. */
  public static UUID clinicId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof ClinicMember member) {
      return member.clinicId();
    }
    throw DomainException.unauthorized("Sign-in required");
  }

  /** Учётная запись сотрудника из сессии (не карточка врача). Без входа — 401. */
  public static UUID staffId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.getPrincipal() instanceof ClinicMember member
        && member.staffId() != null) {
      return member.staffId();
    }
    throw DomainException.unauthorized("Sign-in required");
  }

  public static RuntimeException conflict(String code, String message) {
    return DomainException.conflict(code, message);
  }

  public static RuntimeException badRequest(String code, String message) {
    return DomainException.badRequest(code, message);
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

  public static RuntimeException payloadTooLarge(String code, String message) {
    return DomainException.payloadTooLarge(code, message);
  }

  public static RuntimeException notFound(String code, String message) {
    return DomainException.notFound(code, message);
  }
}
