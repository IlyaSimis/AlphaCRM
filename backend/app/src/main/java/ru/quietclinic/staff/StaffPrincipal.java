package ru.quietclinic.staff;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;
import ru.quietclinic.platform.ClinicMember;

/**
 * Сотрудник в сессии. {@link #getName()} — автор действий для {@code PlatformConfig.actorId()}: у
 * врача это id врача, у остальных — id учётной записи. Клиника — для ограничения выборок.
 */
record StaffPrincipal(UUID userId, UUID actorId, UUID clinicId, String username, String role)
    implements Principal, Serializable, ClinicMember {

  static StaffPrincipal of(StaffUser user) {
    return new StaffPrincipal(user.id, user.actorId(), user.clinicId, user.username, user.role);
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
