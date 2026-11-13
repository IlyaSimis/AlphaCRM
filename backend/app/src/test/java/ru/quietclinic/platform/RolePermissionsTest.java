package ru.quietclinic.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Инварианты матрицы RBAC (раздел C целевой архитектуры) — без Spring. */
class RolePermissionsTest {

  @Test
  void clinicalTextBelongsToDoctorOnly() {
    for (String role : RolePermissions.STAFF_ROLES) {
      boolean expected = RolePermissions.DOCTOR.equals(role);
      assertThat(RolePermissions.of(role).contains(Permission.CLINICAL_RECORD))
          .as(role)
          .isEqualTo(expected);
    }
  }

  @Test
  void ownerHasEverythingOperationalOfAdministratorPlusPrivilegedRoles() {
    Set<Permission> owner = RolePermissions.of(RolePermissions.OWNER);
    assertThat(owner).containsAll(RolePermissions.of(RolePermissions.ADMINISTRATOR));
    assertThat(owner).contains(Permission.ROLE_ASSIGN_PRIVILEGED);
    assertThat(RolePermissions.of(RolePermissions.SENIOR_ADMINISTRATOR))
        .doesNotContain(Permission.ROLE_ASSIGN_PRIVILEGED);
  }

  @Test
  void supportSeesNoPatientsAndCashierNoContacts() {
    Set<Permission> support = RolePermissions.of(RolePermissions.SUPPORT);
    assertThat(support)
        .doesNotContain(
            Permission.PATIENT_READ,
            Permission.PATIENT_READ_CARE,
            Permission.PATIENT_CONTACTS_READ,
            Permission.AUDIT_READ);
    assertThat(support).contains(Permission.AUDIT_READ_PLATFORM, Permission.SCHEDULE_READ);
    assertThat(RolePermissions.of(RolePermissions.CASHIER))
        .contains(Permission.PATIENT_READ)
        .doesNotContain(Permission.PATIENT_CONTACTS_READ);
  }

  @Test
  void managerCannotEscalateToAdministrator() {
    Set<Permission> manager = RolePermissions.of(RolePermissions.MANAGER);
    assertThat(RolePermissions.mayAssign(manager, RolePermissions.DOCTOR)).isTrue();
    assertThat(RolePermissions.mayAssign(manager, RolePermissions.ADMINISTRATOR)).isFalse();
    assertThat(RolePermissions.mayAssign(manager, RolePermissions.OWNER)).isFalse();
    Set<Permission> administrator = RolePermissions.of(RolePermissions.ADMINISTRATOR);
    assertThat(RolePermissions.mayAssign(administrator, RolePermissions.SUPPORT)).isTrue();
    assertThat(RolePermissions.mayAssign(administrator, RolePermissions.SENIOR_ADMINISTRATOR))
        .isFalse();
    Set<Permission> owner = RolePermissions.of(RolePermissions.OWNER);
    assertThat(RolePermissions.mayAssign(owner, RolePermissions.OWNER)).isTrue();
  }

  @Test
  void unknownRolesHaveNoPermissions() {
    assertThat(RolePermissions.of("BOT")).isEmpty();
    assertThat(RolePermissions.of("SYSTEM")).isEmpty();
    assertThat(RolePermissions.of(Set.of("CASHIER", "NURSE")))
        .contains(Permission.INVOICE_WRITE, Permission.INVENTORY_DOCUMENT_WRITE);
  }

  @Test
  void everyStaffRoleHasAPersonalWorkspace() {
    for (String role : RolePermissions.STAFF_ROLES) {
      assertThat(RolePermissions.of(role)).as(role).contains(Permission.WORKSPACE_USE);
    }
  }

  @Test
  void contractsAreAvailableToEveryoneAndInventoryOnlyToItsOperators() {
    Set<String> inventoryRoles =
        Set.of(
            "OWNER", "SENIOR_ADMINISTRATOR", "ADMINISTRATOR", "MANAGER", "NURSE", "SENIOR_NURSE");
    for (String role : RolePermissions.STAFF_ROLES) {
      assertThat(RolePermissions.of(role)).as(role).contains(Permission.CONTRACT_USE);
      assertThat(RolePermissions.of(role).contains(Permission.INVENTORY_CATALOG_READ))
          .as(role)
          .isEqualTo(inventoryRoles.contains(role));
    }
    assertThat(RolePermissions.of(RolePermissions.SENIOR_NURSE))
        .isEqualTo(RolePermissions.of(RolePermissions.NURSE));
  }

  @Test
  void permissionCodesAreUniqueAndDotted() {
    Set<String> codes = new HashSet<>();
    for (Permission permission : Permission.values()) {
      assertThat(permission.code()).matches("[a-z]+\\.[a-z-]+\\.[a-z-]+");
      assertThat(codes.add(permission.code())).as(permission.code()).isTrue();
    }
  }
}
