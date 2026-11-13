package ru.quietclinic.staff;

import java.io.Serializable;
import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import ru.quietclinic.platform.ClinicMember;

/**
 * Сотрудник в сессии. {@link #getName()} — автор действий для {@code PlatformConfig.actorId()}: у
 * врача это id врача, у остальных — id учётной записи. Клиника — для ограничения выборок. roles —
 * все роли сотрудника: по ним — полномочия ROLE_* и разрешения.
 */
record StaffPrincipal(
    UUID userId, UUID actorId, UUID clinicId, String username, String role, Set<String> roles)
    implements Principal, Serializable, ClinicMember {

  static StaffPrincipal of(StaffUser user) {
    return new StaffPrincipal(
        user.id, user.actorId(), user.clinicId, user.username, user.role, user.roleSet());
  }

  /** Полномочия Spring Security: ROLE_ на каждую роль. */
  List<GrantedAuthority> authorities() {
    return roles.stream()
        .sorted()
        .<GrantedAuthority>map(item -> new SimpleGrantedAuthority("ROLE_" + item))
        .toList();
  }

  @Override
  public UUID staffId() {
    return userId;
  }

  @Override
  public String getName() {
    return actorId.toString();
  }
}
