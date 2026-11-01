package ru.quietclinic.integrations;

/** Несекретные настройки Telegram или MAX одной клиники. */
public record MessengerSettings(
    String apiUrl, String publicUrl, String relayUrl, boolean reminderButtons) {}
