package ru.quietclinic.staff;

import java.util.List;
import java.util.UUID;
import ru.quietclinic.platform.Permission;
import ru.quietclinic.platform.RolePermissions;

/**
 * Сотрудник без хеша пароля. clinicName — для шапки интерфейса; timeZone — пояс клиники, в нём
 * интерфейс показывает время. permissions — коды разрешений роли (раздел C целевой архитектуры):
 * интерфейс по ним только прячет кнопки, решение принимает сервер.
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
    String timeZone,
    List<String> permissions,
    String permissionsVersion) {

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
        timeZone,
        RolePermissions.of(user.role).stream().map(Permission::code).sorted().toList(),
        RolePermissions.VERSION);
  }
}
