package ru.quietclinic.inventory;

import java.util.UUID;

record WarehouseView(UUID id, String name, boolean isDefault) {
  static WarehouseView of(Warehouse w) {
    return new WarehouseView(w.id, w.name, w.defaultWarehouse);
  }
}
