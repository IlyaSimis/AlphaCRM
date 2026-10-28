package ru.quietclinic.inventory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Марка без связи с приёмом и пациентом. */
record MarkingCodeView(
    String sgtin,
    UUID itemId,
    String itemName,
    String batch,
    LocalDate expiresOn,
    String status,
    UUID warehouseId,
    Instant updatedAt) {}
