package ru.quietclinic.clinical;

import java.time.Instant;
import java.util.UUID;

/**
 * Строка блока «Документы PDF» и архива: имя, размер, состояние, кто загрузил и кто подписал.
 * Байтов файла здесь нет — только в отдельной выгрузке.
 */
record PatientFileView(
    UUID id,
    UUID patientId,
    String patientName,
    UUID appointmentId,
    String originalName,
    long sizeBytes,
    String state,
    String contentHash,
    UUID uploadedBy,
    String uploadedByName,
    Instant uploadedAt,
    UUID signedBy,
    String signedByName,
    Instant signedAt) {}
