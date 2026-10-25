package ru.quietclinic.communications;

import java.util.Set;

/** Шаблоны сообщений пациенту и чека. Тексты для пациента — в patient-notices.properties. */
final class NoticeTemplates {
  static final String CONFIRMED = "appointment-confirmed";
  static final String REMINDER = "appointment-reminder";
  static final String CANCELLED = "appointment-cancelled";
  static final String RECEIPT = "receipt";

  static final String NOTIFICATION = "NOTIFICATION";
  static final String FISCALIZATION = "FISCALIZATION";

  /** Сообщения о записи, которые теряют смысл после переноса. */
  static final Set<String> ABOUT_TIME = Set.of(CONFIRMED, REMINDER);

  private NoticeTemplates() {}
}
