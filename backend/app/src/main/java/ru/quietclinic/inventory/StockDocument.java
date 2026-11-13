package ru.quietclinic.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Складской документ (накладная). Черновик правится; проведённый неизменяем — исправление новым
 * документом. Проведение идемпотентно: повтор возвращает уже проведённый документ.
 */
@Entity
@Table(name = "stock_document", schema = "inventory")
class StockDocument {
  static final String RECEIPT = "RECEIPT";
  static final String CONSUMPTION = "CONSUMPTION";
  static final String WRITE_OFF = "WRITE_OFF";
  static final String TRANSFER = "TRANSFER";
  static final String RETURN_TO_SUPPLIER = "RETURN_TO_SUPPLIER";
  static final Set<String> TYPES =
      Set.of(RECEIPT, CONSUMPTION, WRITE_OFF, TRANSFER, RETURN_TO_SUPPLIER);

  static final String DRAFT = "DRAFT";
  static final String POSTED = "POSTED";
  static final String CANCELLED = "CANCELLED";

  @Id UUID id;

  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  String type;

  @Column(nullable = false)
  String status;

  @Column(nullable = false)
  LocalDate documentDate;

  String supplier;

  /** Приём, по которому списано (только id: пациента и диагноза склад не знает). */
  UUID appointmentId;

  UUID fromWarehouseId;

  UUID toWarehouseId;

  String note;

  @Column(nullable = false)
  String idempotencyKey;

  @Column(nullable = false)
  UUID createdBy;

  @Column(nullable = false)
  Instant createdAt;

  UUID postedBy;

  Instant postedAt;

  @Version long version;

  protected StockDocument() {}

  StockDocument(UUID clinicId, String type, LocalDate date, String idempotencyKey, UUID createdBy) {
    id = UUID.randomUUID();
    this.clinicId = clinicId;
    this.type = type;
    this.documentDate = date;
    this.idempotencyKey = idempotencyKey;
    this.createdBy = createdBy;
    status = DRAFT;
    createdAt = Instant.now();
  }

  /** Расход со склада: списание, применение, возврат, перемещение. */
  boolean decreases() {
    return !RECEIPT.equals(type);
  }

  boolean increases() {
    return RECEIPT.equals(type) || TRANSFER.equals(type);
  }
}
