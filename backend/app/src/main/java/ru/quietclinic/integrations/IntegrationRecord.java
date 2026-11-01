package ru.quietclinic.integrations;

import java.util.UUID;

/** Не секретная часть подключения клиники к внешнему провайдеру. */
record IntegrationRecord(
    UUID id,
    UUID clinicId,
    String type,
    String status,
    UUID publicKey,
    String settings,
    Integer activeSecretVersion) {}
