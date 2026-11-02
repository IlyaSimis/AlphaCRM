package ru.quietclinic.integrations;

import java.util.UUID;

/** Полная конфигурация канала; используется только внутри backend при доставке. */
public record MessengerIntegration(
    UUID publicKey, MessengerSettings settings, MessengerSecrets secrets, boolean active) {}
