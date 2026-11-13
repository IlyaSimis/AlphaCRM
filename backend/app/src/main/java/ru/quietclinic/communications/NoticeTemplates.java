package ru.quietclinic.communications;

import java.util.Set;

/** Шаблоны сообщений пациенту и чека. Тексты для пациента — в patient-notices.properties. */
final class NoticeTemplates {
  static final String CONFIRMED = "appointment-confirmed";
  static final String REMINDER = "appointment-reminder";
  static final String REMINDER_3H = "appointment-reminder-3h";
  static final String CANCELLED = "appointment-cancelled";
  static final String RECEIPT = "receipt";
  static final String REFUND_RECEIPT = "refund-receipt";

  static final String NOTIFICATION = "NOTIFICATION";
  static final String FISCALIZATION = "FISCALIZATION";

  static final Set<String> REMINDERS = Set.of(REMINDER, REMINDER_3H);

  /** Сообщения о записи, которые теряют смысл после переноса. */
  static final Set<String> ABOUT_TIME = Set.of(CONFIRMED, REMINDER, REMINDER_3H);

  static boolean isReminder(String template) {
    return REMINDERS.contains(template);
  }

  private NoticeTemplates() {}
}
