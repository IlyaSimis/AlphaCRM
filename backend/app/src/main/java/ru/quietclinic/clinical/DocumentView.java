package ru.quietclinic.clinical;

import java.time.Instant;
import java.util.UUID;

record DocumentView(
    UUID id,
    UUID encounterId,
    UUID amendsDocumentId,
    String state,
    String templateVersion,
    String contentHash,
    UUID signedBy,
    Instant signedAt,
    long version) {

  static DocumentView of(MedicalDocument d) {
    return new DocumentView(
        d.id,
        d.encounterId,
        d.amendsDocumentId,
        d.state,
        d.templateVersion,
        d.contentHash,
        d.signedBy,
        d.signedAt,
        d.version);
  }
}
