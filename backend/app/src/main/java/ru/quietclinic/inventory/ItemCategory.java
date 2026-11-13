package ru.quietclinic.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Категория номенклатуры; вид задаёт правила (лекарства, медизделия, расходники, товары). */
@Entity
@Table(name = "item_category", schema = "inventory")
class ItemCategory {
  static final Set<String> KINDS = Set.of("DRUG", "MEDICAL_DEVICE", "CONSUMABLE", "GOODS");

  @Id UUID id;

  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  String name;

  UUID parentId;

  @Column(nullable = false)
  String kind;

  @Column(nullable = false)
  Instant createdAt;

  protected ItemCategory() {}

  ItemCategory(UUID clinicId, String name, UUID parentId, String kind) {
    id = UUID.randomUUID();
    this.clinicId = clinicId;
    this.name = name;
    this.parentId = parentId;
    this.kind = kind;
    createdAt = Instant.now();
  }
}
