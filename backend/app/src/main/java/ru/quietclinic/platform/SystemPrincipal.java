package ru.quietclinic.platform;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/**
 * Автор фоновой обработки события: клиника и автор исходного действия. Роль SYSTEM не даёт ни
 * одного разрешения (RolePermissions), поэтому HTTP-проверки прав для неё всегда закрыты.
 */
record SystemPrincipal(UUID clinicId, UUID actorId)
    implements Principal, Serializable, ClinicMember {
  static final String ROLE = "SYSTEM";

  @Override
  public String getName() {
    return actorId.toString();
  }
}
