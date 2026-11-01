package ru.quietclinic.integrations;

/** Полная конфигурация канала; используется только внутри backend при доставке. */
public record MessengerIntegration(
    MessengerSettings settings, MessengerSecrets secrets, boolean active) {}
