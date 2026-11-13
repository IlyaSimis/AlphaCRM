package ru.quietclinic.staffchat;

/** Персональные каналы уведомлений; общий выключатель не меняет остальные сохранённые значения. */
record StaffChatPreferencesRequest(
    boolean notificationsEnabled, boolean soundEnabled, boolean browserEnabled) {}
