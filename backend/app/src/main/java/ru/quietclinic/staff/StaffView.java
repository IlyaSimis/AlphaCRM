package ru.quietclinic.staff;

import java.util.List;
import java.util.UUID;
import ru.quietclinic.platform.Permission;
import ru.quietclinic.platform.RolePermissions;

/**
 * Сотрудник без хеша пароля. clinicName — для шапки интерфейса; timeZone — пояс клиники, в нём
 * интерфейс показывает время. role — основная роль, roles — все роли сотрудника; permissions —
 * объединение разрешений всех ролей (раздел C целевой архитектуры): интерфейс по ним только прячет
 * кнопки, решение принимает сервер.
 */
record StaffView(
    UUID id,
    String fullName,
    String username,
    String role,
    List<String> roles,
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
        user.orderedRoles(),
        user.practitionerId,
        user.active,
        user.mustChangePassword,
        clinicName,
        timeZone,
        RolePermissions.of(user.roleSet()).stream().map(Permission::code).sorted().toList(),
        RolePermissions.VERSION);
  }
}
