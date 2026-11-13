package ru.quietclinic.inventory;

import java.util.UUID;

record CategoryView(UUID id, String name, UUID parentId, String kind) {
  static CategoryView of(ItemCategory c) {
    return new CategoryView(c.id, c.name, c.parentId, c.kind);
  }
}
