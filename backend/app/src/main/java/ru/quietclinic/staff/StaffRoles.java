package ru.quietclinic.staff;

import java.util.Set;
import ru.quietclinic.platform.RolePermissions;

final class StaffRoles {
  static final String DOCTOR = RolePermissions.DOCTOR;
  static final Set<String> ALL = Set.copyOf(RolePermissions.STAFF_ROLES);

  private StaffRoles() {}
}
