package ru.quietclinic.inventory;

import java.time.LocalDate;
import java.util.UUID;

/** Разобранная марка и позиция номенклатуры по GTIN; codeStatus — если марка уже известна. */
record ScanView(
    String sgtin,
    String gtin,
    String serial,
    String batch,
    LocalDate expiresOn,
    UUID itemId,
    String itemName,
    String codeStatus,
    UUID warehouseId) {}
