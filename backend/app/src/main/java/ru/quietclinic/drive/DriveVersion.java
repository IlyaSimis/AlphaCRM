package ru.quietclinic.drive;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Версия файла: ключ объекта в хранилище, размер открытых байтов и их SHA-256. */
@Entity
@Table(name = "drive_version", schema = "drive")
class DriveVersion {
  @Id UUID id;

  @Column(nullable = false)
  UUID nodeId;

  @Column(name = "version_no", nullable = false)
  int number;

  @Column(nullable = false)
  String objectKey;

  @Column(nullable = false)
  long sizeBytes;

  @Column(nullable = false)
  String sha256;

  String contentType;

  @Column(nullable = false)
  UUID uploadedBy;

  @Column(nullable = false)
  Instant uploadedAt;

  protected DriveVersion() {}

  DriveVersion(
      UUID nodeId,
      int number,
      String objectKey,
      long sizeBytes,
      String sha256,
      String contentType,
      UUID uploadedBy) {
    id = UUID.randomUUID();
    this.nodeId = nodeId;
    this.number = number;
    this.objectKey = objectKey;
    this.sizeBytes = sizeBytes;
    this.sha256 = sha256;
    this.contentType = contentType;
    this.uploadedBy = uploadedBy;
    uploadedAt = Instant.now();
  }
}
