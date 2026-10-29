package ru.quietclinic.platform;

import static ru.quietclinic.platform.Permission.*;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Роли и их разрешения — матрица RBAC из docs/target-architecture.md (раздел C). Набор зашит в коде
 * и версионируется им: в интерфейсе не редактируется. Поменяли набор — поднимите {@link #VERSION}.
 *
 * <p>«Администратору и владельцу — всё» означает всё операционное: клинический текст (заметки,
 * диагнозы, документы приёма) остаётся только у врача со связью с лечением.
 */
public final class RolePermissions {
  public static final String OWNER = "OWNER";
  public static final String SENIOR_ADMINISTRATOR = "SENIOR_ADMINISTRATOR";
  public static final String ADMINISTRATOR = "ADMINISTRATOR";
  public static final String MANAGER = "MANAGER";
  public static final String DOCTOR = "DOCTOR";
  public static final String NURSE = "NURSE";
  public static final String CASHIER = "CASHIER";
  public static final String SUPPORT = "SUPPORT";

  /** Версия набора разрешений: отдаётся в /auth/me как permissionsVersion. */
  public static final String VERSION = "2026-10-29.2";

  /** Роли сотрудников в порядке экрана «Сотрудники». */
  public static final List<String> STAFF_ROLES =
      List.of(OWNER, SENIOR_ADMINISTRATOR, ADMINISTRATOR, MANAGER, DOCTOR, NURSE, CASHIER, SUPPORT);

  /** Роли, которые назначает только владелец. */
  public static final Set<String> PRIVILEGED = Set.of(OWNER, SENIOR_ADMINISTRATOR);

  /** Роли, которые назначают владелец, старший администратор и администратор. */
  public static final Set<String> ADMIN_LEVEL = Set.of(ADMINISTRATOR, SUPPORT);

  private static final Map<String, Set<Permission>> BY_ROLE = build();

  private RolePermissions() {}

  private static Map<String, Set<Permission>> build() {
    var operations =
        EnumSet.of(
            HR_READ_OWN,
            STAFF_READ,
            STAFF_MANAGE,
            ROLE_ASSIGN_ADMIN,
            STAFF_PASSWORD_RESET,
            SCHEDULE_READ,
            APPOINTMENT_WRITE,
            PRACTITIONER_READ,
            PRACTITIONER_MANAGE,
            SERVICE_READ,
            SERVICE_MANAGE,
            REGISTRY_TASK_READ,
            REGISTRY_TASK_MANAGE,
            WAITLIST_READ,
            WAITLIST_MANAGE,
            PATIENT_READ,
            PATIENT_CONTACTS_READ,
            PATIENT_WRITE,
            PATIENT_FILE_WRITE,
            PATIENT_FILE_READ,
            INVOICE_READ,
            INVOICE_WRITE,
            INVOICE_SET_AMOUNT,
            INVOICE_VOID,
            PAYMENT_REFUND,
            REVENUE_READ,
            INVENTORY_CATALOG_READ,
            INVENTORY_CATALOG_WRITE,
            INVENTORY_DOCUMENT_WRITE,
            INVENTORY_ALLOW_NEGATIVE,
            INVENTORY_STOCK_READ,
            MARKING_STATUS_READ,
            MARKING_MANAGE,
            DRIVE_USE,
            WORKSPACE_USE,
            ASSISTANT_USE,
            DRIVE_ADMIN,
            DASHBOARD_READ,
            ANALYTICS_READ,
            AUDIT_READ,
            DELIVERY_READ,
            DELIVERY_RETRY);

    var owner = EnumSet.copyOf(operations);
    owner.add(ROLE_ASSIGN_PRIVILEGED);
    owner.add(HR_MANAGE);
    owner.add(CLINICAL_BREAK_GLASS);
    owner.add(CLINICAL_BREAK_GLASS_REVIEW);

    var senior = EnumSet.copyOf(operations);
    senior.add(CLINICAL_BREAK_GLASS_REVIEW);

    var manager =
        EnumSet.of(
            HR_READ_OWN,
            STAFF_READ,
            STAFF_MANAGE,
            STAFF_PASSWORD_RESET,
            SCHEDULE_READ,
            APPOINTMENT_WRITE,
            PRACTITIONER_READ,
            PRACTITIONER_MANAGE,
            SERVICE_READ,
            REGISTRY_TASK_READ,
            WAITLIST_READ,
            PATIENT_READ,
            PATIENT_CONTACTS_READ,
            PATIENT_WRITE,
            SIGNED_DOCUMENT_READ,
            PATIENT_FILE_READ_SIGNED,
            INVOICE_READ,
            REVENUE_READ,
            INVENTORY_CATALOG_READ,
            INVENTORY_CATALOG_WRITE,
            INVENTORY_DOCUMENT_WRITE,
            INVENTORY_STOCK_READ,
            MARKING_STATUS_READ,
            MARKING_MANAGE,
            DRIVE_USE,
            WORKSPACE_USE,
            ASSISTANT_USE,
            DASHBOARD_READ,
            ANALYTICS_READ,
            AUDIT_READ);

    var doctor =
        EnumSet.of(
            HR_READ_OWN,
            SCHEDULE_READ,
            APPOINTMENT_WRITE_OWN,
            PRACTITIONER_READ,
            SERVICE_READ,
            PATIENT_READ_CARE,
            PATIENT_CONTACTS_READ,
            CLINICAL_RECORD,
            SIGNED_DOCUMENT_READ_OWN,
            PATIENT_FILE_WRITE_CARE,
            INVENTORY_CATALOG_READ,
            INVENTORY_STOCK_READ,
            DRIVE_USE,
            WORKSPACE_USE,
            ASSISTANT_USE,
            DASHBOARD_READ,
            ANALYTICS_READ_OWN);

    var nurse =
        EnumSet.of(
            HR_READ_OWN,
            SCHEDULE_READ,
            APPOINTMENT_WRITE,
            PRACTITIONER_READ,
            SERVICE_READ,
            PATIENT_READ,
            PATIENT_CONTACTS_READ,
            INVENTORY_CATALOG_READ,
            INVENTORY_CATALOG_WRITE,
            INVENTORY_DOCUMENT_WRITE,
            INVENTORY_STOCK_READ,
            MARKING_STATUS_READ,
            DRIVE_USE,
            WORKSPACE_USE,
            ASSISTANT_USE,
            DASHBOARD_READ);

    var cashier =
        EnumSet.of(
            HR_READ_OWN,
            SCHEDULE_READ,
            PRACTITIONER_READ,
            SERVICE_READ,
            PATIENT_READ,
            REGISTRY_TASK_READ,
            INVOICE_READ,
            INVOICE_WRITE,
            INVOICE_SET_AMOUNT,
            DRIVE_USE,
            WORKSPACE_USE,
            ASSISTANT_USE,
            DASHBOARD_READ);

    var support =
        EnumSet.of(
            HR_READ_OWN,
            STAFF_READ,
            STAFF_PASSWORD_RESET_PLATFORM,
            SCHEDULE_READ,
            PRACTITIONER_READ,
            SERVICE_READ,
            INVENTORY_CATALOG_READ,
            INVENTORY_STOCK_READ,
            MARKING_STATUS_READ,
            MARKING_MANAGE,
            DRIVE_USE,
            WORKSPACE_USE,
            ASSISTANT_USE,
            DRIVE_ADMIN,
            DASHBOARD_READ,
            ANALYTICS_READ,
            AUDIT_READ_PLATFORM,
            DELIVERY_READ,
            DELIVERY_RETRY);

    var map = new LinkedHashMap<String, Set<Permission>>();
    map.put(OWNER, Set.copyOf(owner));
    map.put(SENIOR_ADMINISTRATOR, Set.copyOf(senior));
    map.put(ADMINISTRATOR, Set.copyOf(operations));
    map.put(MANAGER, Set.copyOf(manager));
    map.put(DOCTOR, Set.copyOf(doctor));
    map.put(NURSE, Set.copyOf(nurse));
    map.put(CASHIER, Set.copyOf(cashier));
    map.put(SUPPORT, Set.copyOf(support));
    return Map.copyOf(map);
  }

  /** Разрешения роли; неизвестная роль (в том числе BOT) — пустой набор. */
  public static Set<Permission> of(String role) {
    return BY_ROLE.getOrDefault(role, Set.of());
  }

  /** Объединение разрешений нескольких ролей. */
  public static Set<Permission> of(Collection<String> roles) {
    var result = EnumSet.noneOf(Permission.class);
    roles.forEach(role -> result.addAll(of(role)));
    return result;
  }

  public static boolean isStaffRole(String role) {
    return BY_ROLE.containsKey(role);
  }

  /**
   * Можно ли сотруднику с этими разрешениями назначить роль (и выключать, переназначать пароль
   * сотруднику с этой ролью). Владельца и старшего администратора — только владелец; администратора
   * и техподдержку — владелец, старший администратор и администратор; остальные роли — все, кто
   * заводит сотрудников.
   */
  public static boolean mayAssign(Set<Permission> actor, String role) {
    if (PRIVILEGED.contains(role)) return actor.contains(ROLE_ASSIGN_PRIVILEGED);
    if (ADMIN_LEVEL.contains(role)) return actor.contains(ROLE_ASSIGN_ADMIN);
    return actor.contains(STAFF_MANAGE);
  }
}
