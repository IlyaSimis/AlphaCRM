package ru.quietclinic.bot;

/** Клиника для бота: название, часовой пояс расписания и услуга онлайн-записи. */
record BotClinicView(String name, String timeZone, String serviceName) {}
