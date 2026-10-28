package ru.quietclinic.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Накладная со строками. shortages — позиции, ушедшие в минус при проведении расхода. */
record DocumentView(
    UUID id,
    String type,
    String status,
    LocalDate documentDate,
    String supplier,
    UUID appointmentId,
    UUID fromWarehouseId,
    UUID toWarehouseId,
    String note,
    Instant createdAt,
    Instant postedAt,
    List<LineView> lines,
    List<Shortage> shortages) {

  record LineView(
      UUID id,
      UUID itemId,
      String itemName,
      String unit,
      boolean marked,
      BigDecimal quantity,
      String batch,
      LocalDate expiresOn,
      List<String> codes) {}

  record Shortage(UUID itemId, String itemName, BigDecimal missing) {}
}
