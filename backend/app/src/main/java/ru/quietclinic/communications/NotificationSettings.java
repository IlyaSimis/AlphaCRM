package ru.quietclinic.communications;

/** Какие автоматические напоминания включены для клиники. */
record NotificationSettings(boolean reminder24hEnabled, boolean reminder3hEnabled) {
  static final NotificationSettings DEFAULT = new NotificationSettings(true, false);
}
