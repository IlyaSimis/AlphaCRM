package ru.quietclinic.communications;

import java.util.UUID;

/** Сообщение, помеченное к отправке: строка outbox и, для пациента, собранный текст. */
record Prepared(
    String topic,
    String template,
    UUID clinicId,
    OutboxMessage message,
    PatientMessage patientMessage) {}
