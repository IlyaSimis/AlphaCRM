package ru.quietclinic.staff;

import java.util.Set;

final class StaffRoles {
  static final String DOCTOR = "DOCTOR";
  static final Set<String> ALL = Set.of("ADMINISTRATOR", DOCTOR, "MANAGER", "CASHIER");

  private StaffRoles() {}
}
