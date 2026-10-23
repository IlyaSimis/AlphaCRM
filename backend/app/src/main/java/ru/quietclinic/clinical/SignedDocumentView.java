package ru.quietclinic.clinical;

import java.time.Instant;
import java.util.UUID;

/**
 * Подписанный документ из архива: поля берутся из зашифрованного снимка, а не из текущей заметки.
 * amendsDocumentId — исходный документ, если это дополнение.
 */
record SignedDocumentView(
    UUID id,
    UUID encounterId,
    UUID appointmentId,
    UUID patientId,
    String patientName,
    UUID practitionerId,
    String practitionerName,
    Instant signedAt,
    String templateVersion,
    UUID amendsDocumentId,
    String contentHash,
    String complaints,
    String mentalStatus,
    String diagnosis,
    String recommendations) {}
