package ru.quietclinic.inventory;

import java.math.BigDecimal;
import java.util.UUID;

/** Позиция с категорией и общим остатком по всем складам. */
record ItemView(
    UUID id,
    UUID categoryId,
    String categoryName,
    String kind,
    String name,
    String unit,
    String gtin,
    boolean marked,
    String markingSystem,
    boolean rxOnly,
    boolean active,
    BigDecimal stock) {}
