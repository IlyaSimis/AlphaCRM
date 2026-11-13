package ru.quietclinic.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Позиция номенклатуры. Маркируемая позиция (marked) требует GTIN и систему маркировки: МДЛП для
 * лекарств, ГИС МТ для товаров; при движении по ней сканируют марку каждой упаковки.
 */
@Entity
@Table(name = "item", schema = "inventory")
class Item {
  static final Set<String> MARKING_SYSTEMS = Set.of("NONE", "MDLP", "GIS_MT");

  @Id UUID id;

  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  UUID categoryId;

  @Column(nullable = false)
  String name;

  @Column(nullable = false)
  String unit;

  String gtin;

  @Column(nullable = false)
  boolean marked;

  @Column(nullable = false)
  String markingSystem;

  @Column(nullable = false)
  boolean rxOnly;

  @Column(nullable = false)
  boolean active;

  @Column(nullable = false)
  BigDecimal minStock = BigDecimal.ZERO;

  @Column(nullable = false)
  Instant createdAt;

  protected Item() {}

  Item(UUID clinicId, UUID categoryId, String name, String unit) {
    id = UUID.randomUUID();
    this.clinicId = clinicId;
    this.categoryId = categoryId;
    this.name = name;
    this.unit = unit;
    markingSystem = "NONE";
    active = true;
    createdAt = Instant.now();
  }
}
