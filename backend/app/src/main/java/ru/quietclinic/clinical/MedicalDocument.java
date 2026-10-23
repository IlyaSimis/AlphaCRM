package ru.quietclinic.clinical;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Entity
@Table(name = "medical_document")
class MedicalDocument {
  @Id UUID id;

  @Column(nullable = false)
  UUID encounterId;

  UUID amendsDocumentId;

  @Column(nullable = false)
  String state;

  @Column(nullable = false)
  String templateVersion;

  @Column(nullable = false, length = 32000)
  String snapshotEncrypted;

  String contentHash;
  UUID signedBy;
  Instant signedAt;
  Instant createdAt;

  @Version long version;

  protected MedicalDocument() {}

  MedicalDocument(
      UUID encounterId, UUID amendsDocumentId, String templateVersion, String snapshotEncrypted) {
    id = UUID.randomUUID();
    this.encounterId = encounterId;
    this.amendsDocumentId = amendsDocumentId;
    this.templateVersion = templateVersion;
    this.snapshotEncrypted = snapshotEncrypted;
    state = "DRAFT";
    createdAt = Instant.now();
  }

  void updateSnapshot(String snapshotEncrypted) {
    if (!state.equals("DRAFT"))
      throw conflict("DOCUMENT_LOCKED", "A signed document cannot be changed");
    this.snapshotEncrypted = snapshotEncrypted;
  }

  void sign(UUID signer, String canonicalPlaintext) {
    if (!state.equals("DRAFT")) throw conflict("DOCUMENT_LOCKED", "Document is already signed");
    try {
      contentHash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(canonicalPlaintext.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
    signedBy = signer;
    signedAt = Instant.now();
    state = "SIGNED_LOCKED";
  }
}
