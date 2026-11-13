package ru.quietclinic.inventory;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Строка накладной: позиция, количество, партия и срок; для маркируемых — марки упаковок. */
@Entity
@Table(name = "stock_line", schema = "inventory")
class StockLine {
  @Id UUID id;

  @Column(nullable = false)
  UUID documentId;

  @Column(nullable = false)
  int lineNo;

  @Column(nullable = false)
  UUID itemId;

  @Column(nullable = false)
  BigDecimal quantity;

  @Column(nullable = false)
  String batch;

  LocalDate expiresOn;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "stock_line_code",
      schema = "inventory",
      joinColumns = @JoinColumn(name = "line_id"))
  @Column(name = "sgtin")
  Set<String> codes = new LinkedHashSet<>();

  protected StockLine() {}

  StockLine(
      UUID documentId,
      int lineNo,
      UUID itemId,
      BigDecimal quantity,
      String batch,
      LocalDate expiresOn,
      Set<String> codes) {
    id = UUID.randomUUID();
    this.documentId = documentId;
    this.lineNo = lineNo;
    this.itemId = itemId;
    this.quantity = quantity;
    this.batch = batch == null ? "" : batch.trim();
    this.expiresOn = expiresOn;
    if (codes != null) this.codes.addAll(codes);
  }
}
