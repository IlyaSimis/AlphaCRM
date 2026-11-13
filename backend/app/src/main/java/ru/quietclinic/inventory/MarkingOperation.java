package ru.quietclinic.inventory;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Исходящая операция маркировки — строка outbox к оператору: тип по смыслу, марки, попытки, ссылка
 * оператора и код последней ошибки. Отправляет {@link MarkingRelay} через {@link MarkingPort}.
 */
@Entity
@Table(name = "marking_operation", schema = "inventory")
class MarkingOperation {
  static final String ACCEPTANCE = "ACCEPTANCE";
  static final String WITHDRAW_MEDICAL_USE = "WITHDRAW_MEDICAL_USE";
  static final String WRITE_OFF = "WRITE_OFF";
  static final String RETURN_TO_SUPPLIER = "RETURN_TO_SUPPLIER";

  static final String PENDING = "PENDING";
  static final String DONE = "DONE";
  static final String ERROR = "ERROR";

  @Id UUID id;

  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  String type;

  @Column(nullable = false)
  UUID documentId;

  @Column(nullable = false)
  String status;

  @Column(nullable = false)
  int attempts;

  @Column(nullable = false)
  Instant nextAttemptAt;

  String operatorRef;

  String lastError;

  @Column(nullable = false)
  Instant createdAt;

  @Column(nullable = false)
  Instant updatedAt;

  @Version long version;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "marking_operation_code",
      schema = "inventory",
      joinColumns = @JoinColumn(name = "operation_id"))
  @Column(name = "sgtin")
  Set<String> sgtins = new LinkedHashSet<>();

  protected MarkingOperation() {}

  MarkingOperation(UUID clinicId, String type, UUID documentId, Set<String> sgtins) {
    id = UUID.randomUUID();
    this.clinicId = clinicId;
    this.type = type;
    this.documentId = documentId;
    this.sgtins.addAll(sgtins);
    status = PENDING;
    createdAt = Instant.now();
    updatedAt = createdAt;
    nextAttemptAt = createdAt;
  }
}
