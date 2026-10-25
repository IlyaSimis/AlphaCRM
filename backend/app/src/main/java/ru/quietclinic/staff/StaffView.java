package ru.quietclinic.staff;

import java.util.UUID;

/**
 * Сотрудник без хеша пароля. clinicName — для шапки интерфейса; timeZone — пояс клиники, в нём
 * интерфейс показывает время.
 */
record StaffView(
    UUID id,
    String fullName,
    String username,
    String role,
    UUID practitionerId,
    boolean active,
    boolean mustChangePassword,
    String clinicName,
    String timeZone) {

  static StaffView of(StaffUser user, String clinicName, String timeZone) {
    return new StaffView(
        user.id,
        user.fullName,
        user.username,
        user.role,
        user.practitionerId,
        user.active,
        user.mustChangePassword,
        clinicName,
        timeZone);
  }
}
