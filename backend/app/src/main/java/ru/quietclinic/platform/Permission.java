package ru.quietclinic.platform;

/**
 * Разрешение вида {@code <сервис>.<ресурс>.<действие>} (docs/target-architecture.md, раздел C).
 * Сервис проверяет разрешение ({@link PlatformConfig#requirePermission}) и затем атрибуты: своя
 * клиника, своя запись, связь с лечением. Набор разрешений роли — в {@link RolePermissions}.
 */
public enum Permission {
  // identity
  STAFF_READ("identity.staff.read"),
  STAFF_MANAGE("identity.staff.manage"),
  /** Назначить и обслуживать роли ADMINISTRATOR и SUPPORT. */
  ROLE_ASSIGN_ADMIN("identity.role.assign-admin"),
  /** Назначить и обслуживать роли OWNER и SENIOR_ADMINISTRATOR. */
  ROLE_ASSIGN_PRIVILEGED("identity.role.assign-privileged"),
  STAFF_PASSWORD_RESET("identity.staff.reset-password"),
  /** Условия работы и ставки всех сотрудников: только владелец (раздел C, hr). */
  HR_MANAGE("identity.hr.manage"),
  /** Свои условия работы и ставка: каждый сотрудник. */
  HR_READ_OWN("identity.hr.read-own"),
  /** Сброс пароля техподдержкой: любому, кроме владельца. */
  STAFF_PASSWORD_RESET_PLATFORM("identity.staff.reset-password-platform"),

  // schedule
  SCHEDULE_READ("schedule.appointment.read"),
  APPOINTMENT_WRITE("schedule.appointment.write"),
  /** Врач: записать, перенести, отменить и сменить статус только своих записей. */
  APPOINTMENT_WRITE_OWN("schedule.appointment.write-own"),
  PRACTITIONER_READ("schedule.practitioner.read"),
  PRACTITIONER_MANAGE("schedule.practitioner.manage"),
  SERVICE_READ("schedule.service.read"),
  /** Цены, свободная цена, услуги. */
  SERVICE_MANAGE("schedule.service.manage"),

  // registry (входит в schedule)
  REGISTRY_TASK_READ("schedule.registry-task.read"),
  REGISTRY_TASK_MANAGE("schedule.registry-task.manage"),
  WAITLIST_READ("schedule.waitlist.read"),
  WAITLIST_MANAGE("schedule.waitlist.manage"),

  // clinical
  /** Административная карточка любого пациента клиники. */
  PATIENT_READ("clinical.patient.read"),
  /** Административная карточка только при активной связи с лечением. */
  PATIENT_READ_CARE("clinical.patient.read-care"),
  /** Дата рождения, контакты, согласия, поля регистратуры; без него — только ФИО. */
  PATIENT_CONTACTS_READ("clinical.patient.contacts-read"),
  PATIENT_WRITE("clinical.patient.write"),
  /** Приём, заметка, диагноз, документы приёма — при связи с лечением. */
  /**
   * Экстренный доступ владельца (break-glass, K1): чтение клинической истории одного пациента без
   * связи с лечением, 60 минут, с причиной и повторным вводом пароля.
   */
  CLINICAL_BREAK_GLASS("clinical.record.break-glass"),
  /** Журнал экстренных доступов клиники: владелец и старший администратор. */
  CLINICAL_BREAK_GLASS_REVIEW("clinical.break-glass.review"),
  CLINICAL_RECORD("clinical.note.write"),
  SIGNED_DOCUMENT_READ("clinical.signed-document.read"),
  SIGNED_DOCUMENT_READ_OWN("clinical.signed-document.read-own"),
  PATIENT_FILE_WRITE("clinical.patient-file.write"),
  PATIENT_FILE_WRITE_CARE("clinical.patient-file.write-care"),
  /** Черновики и подписанные PDF всех пациентов клиники. */
  PATIENT_FILE_READ("clinical.patient-file.read"),
  PATIENT_FILE_READ_SIGNED("clinical.patient-file.read-signed"),
  /** Формирование, чтение и печать административных договоров любым сотрудником. */
  CONTRACT_USE("clinical.contract.use"),

  // finance
  INVOICE_READ("finance.invoice.read"),
  INVOICE_WRITE("finance.invoice.write"),
  INVOICE_SET_AMOUNT("finance.invoice.set-amount"),
  /** Аннулировать неоплаченный счёт. */
  INVOICE_VOID("finance.invoice.void"),
  /** Вернуть оплату (полный возврат с чеком возврата). */
  PAYMENT_REFUND("finance.payment.refund"),
  /** Выручка и продажи — только агрегаты. */
  REVENUE_READ("finance.revenue.read"),

  // inventory
  INVENTORY_CATALOG_READ("inventory.item.read"),
  INVENTORY_CATALOG_WRITE("inventory.item.write"),
  INVENTORY_DOCUMENT_WRITE("inventory.document.write"),
  /** Проведение расхода сверх остатка (с событием StockShortage). */
  INVENTORY_ALLOW_NEGATIVE("inventory.document.allow-negative"),
  INVENTORY_STOCK_READ("inventory.stock.read"),
  MARKING_STATUS_READ("inventory.marking.read"),
  MARKING_MANAGE("inventory.marking.manage"),

  // drive
  DRIVE_USE("drive.file.use"),
  DRIVE_ADMIN("drive.clinic.admin"),

  // workspace: личные задачи, заметки и настраиваемые представления
  WORKSPACE_USE("workspace.item.use"),

  // assistant: безопасная навигация без доступа к содержимому страниц
  ASSISTANT_USE("assistant.navigation.use"),

  // insights
  DASHBOARD_READ("insights.dashboard.read"),
  ANALYTICS_READ("insights.analytics.read"),
  ANALYTICS_READ_OWN("insights.analytics.read-own"),

  // audit
  AUDIT_READ("audit.event.read"),
  /** Журнал без объектов клинического контура. */
  AUDIT_READ_PLATFORM("audit.event.read-platform"),

  // communications
  DELIVERY_READ("communications.delivery.read"),
  DELIVERY_RETRY("communications.delivery.retry"),

  // integrations
  INTEGRATION_READ("integration.configuration.read"),
  INTEGRATION_MANAGE("integration.configuration.manage");

  private final String code;

  Permission(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
