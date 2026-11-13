package ru.quietclinic.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Движение и запас по позициям за период; без пациентов и ссылок на приёмы. */
record StockMetricsView(LocalDate from, LocalDate to, List<Row> rows) {
  record Row(
      UUID itemId,
      String itemName,
      String unit,
      BigDecimal stock,
      BigDecimal minStock,
      BigDecimal received,
      BigDecimal consumption,
      BigDecimal writeOff,
      BigDecimal turnover,
      BigDecimal daysStock) {}
}
