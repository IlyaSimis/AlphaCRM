package ru.quietclinic.drive;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Квота клиники: занято (все версии файлов, включая корзину) и предел. */
@Entity
@Table(name = "drive_quota", schema = "drive")
class DriveQuota {
  @Id UUID clinicId;

  @Column(nullable = false)
  long bytesUsed;

  @Column(nullable = false)
  long bytesLimit;

  protected DriveQuota() {}

  DriveQuota(UUID clinicId, long bytesLimit) {
    this.clinicId = clinicId;
    this.bytesLimit = bytesLimit;
  }
}
