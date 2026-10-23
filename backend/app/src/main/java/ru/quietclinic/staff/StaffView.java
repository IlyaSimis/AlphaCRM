package ru.quietclinic.staff;

import java.util.UUID;

/** Сотрудник без хеша пароля. clinicName — для шапки интерфейса. */
record StaffView(
    UUID id,
    String fullName,
    String username,
    String role,
    UUID practitionerId,
    boolean active,
    boolean mustChangePassword,
    String clinicName) {

  static StaffView of(StaffUser user, String clinicName) {
    return new StaffView(
        user.id,
        user.fullName,
        user.username,
        user.role,
        user.practitionerId,
        user.active,
        user.mustChangePassword,
        clinicName);
  }
}
