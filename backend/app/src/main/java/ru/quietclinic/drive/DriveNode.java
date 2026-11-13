package ru.quietclinic.drive;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Папка или файл диска. Удаление — в корзину (deletedAt), окончательно — через срок корзины. */
@Entity
@Table(name = "drive_node", schema = "drive")
class DriveNode {
  static final String FILE = "FILE";
  static final String FOLDER = "FOLDER";

  @Id UUID id;

  @Column(nullable = false)
  UUID clinicId;

  UUID parentId;

  @Column(nullable = false)
  String kind;

  @Column(nullable = false)
  String name;

  /** Учётная запись сотрудника-владельца (staff_user.id): у него всегда MANAGE. */
  @Column(nullable = false)
  UUID ownerId;

  @Column(nullable = false)
  long sizeBytes;

  @Column(nullable = false)
  int currentVersion;

  @Column(nullable = false)
  Instant createdAt;

  @Column(nullable = false)
  Instant updatedAt;

  Instant deletedAt;

  UUID deletedBy;

  @Version long version;

  protected DriveNode() {}

  DriveNode(UUID clinicId, UUID parentId, String kind, String name, UUID ownerId) {
    id = UUID.randomUUID();
    this.clinicId = clinicId;
    this.parentId = parentId;
    this.kind = kind;
    this.name = name;
    this.ownerId = ownerId;
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  boolean folder() {
    return FOLDER.equals(kind);
  }
}
