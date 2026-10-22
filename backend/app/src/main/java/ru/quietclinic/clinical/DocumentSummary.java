package ru.quietclinic.clinical;

import java.time.Instant;
import java.util.UUID;

/** Версия документа приёма без содержимого: snapshot и текст сюда не попадают. */
record DocumentSummary(
    UUID id, String state, String templateVersion, Instant signedAt, UUID amendsDocumentId) {

  static DocumentSummary of(MedicalDocument d) {
    return new DocumentSummary(d.id, d.state, d.templateVersion, d.signedAt, d.amendsDocumentId);
  }
}
