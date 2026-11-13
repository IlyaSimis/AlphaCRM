package ru.quietclinic.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Марка упаковки: SGTIN = GTIN + серийный номер. Статусы: PENDING_ACCEPTANCE → IN_STOCK → RESERVED
 * → WITHDRAWAL_PENDING → WITHDRAWN (или WRITE_OFF_PENDING → WRITTEN_OFF, RETURN_PENDING →
 * RETURNED), ERROR — оператор отказал. Криптохвост не хранится.
 */
@Entity
@Table(name = "marking_code", schema = "inventory")
class MarkingCode {
  @Id UUID id;

  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  String sgtin;

  @Column(nullable = false)
  String gtin;

  @Column(nullable = false)
  String serial;

  @Column(nullable = false)
  UUID itemId;

  @Column(nullable = false)
  String batch;

  LocalDate expiresOn;

  @Column(nullable = false)
  String status;

  UUID warehouseId;

  @Column(nullable = false)
  UUID documentId;

  @Column(nullable = false)
  Instant updatedAt;

  protected MarkingCode() {}

  MarkingCode(
      UUID clinicId,
      Gs1.Parsed code,
      UUID itemId,
      String batch,
      LocalDate expiresOn,
      UUID warehouseId,
      UUID documentId) {
    id = UUID.randomUUID();
    this.clinicId = clinicId;
    this.sgtin = code.sgtin();
    this.gtin = code.gtin();
    this.serial = code.serial();
    this.itemId = itemId;
    this.batch = batch == null ? "" : batch;
    this.expiresOn = expiresOn;
    this.warehouseId = warehouseId;
    this.documentId = documentId;
    status = "PENDING_ACCEPTANCE";
    updatedAt = Instant.now();
  }

  void move(String status, UUID documentId) {
    this.status = status;
    this.documentId = documentId;
    updatedAt = Instant.now();
  }
}
