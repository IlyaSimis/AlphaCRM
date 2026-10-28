package ru.quietclinic.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Остаток позиции на складе по партии; для маркируемых — число марок в наличии. */
record BalanceView(
    UUID itemId,
    String itemName,
    UUID categoryId,
    String categoryName,
    String kind,
    String unit,
    boolean marked,
    UUID warehouseId,
    String warehouseName,
    String batch,
    LocalDate expiresOn,
    BigDecimal quantity,
    long codesInStock) {}
