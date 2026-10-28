package ru.quietclinic.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Место хранения: основной склад, процедурный кабинет. Основной создаётся сам при первом входе. */
@Entity
@Table(name = "warehouse", schema = "inventory")
class Warehouse {
  @Id UUID id;

  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  String name;

  @Column(name = "is_default", nullable = false)
  boolean defaultWarehouse;

  @Column(nullable = false)
  Instant createdAt;

  protected Warehouse() {}

  Warehouse(UUID clinicId, String name, boolean defaultWarehouse) {
    id = UUID.randomUUID();
    this.clinicId = clinicId;
    this.name = name;
    this.defaultWarehouse = defaultWarehouse;
    createdAt = Instant.now();
  }
}
