package ru.quietclinic.billing;

import java.time.LocalDate;
import java.util.List;

/**
 * Продажи за период: строки по дню, услуге или врачу и итог. Суммы в копейках; netMinor — оплаты
 * минус возвраты; openPriceMinor — часть оплат по свободной цене (признак D5).
 */
record SalesReport(
    LocalDate from,
    LocalDate to,
    String groupBy,
    String currency,
    List<Line> lines,
    Totals totals) {

  record Line(
      String key,
      String label,
      long payments,
      long amountMinor,
      long refunds,
      long refundedMinor,
      long netMinor,
      long openPriceMinor) {}

  record Totals(
      long payments,
      long amountMinor,
      long refunds,
      long refundedMinor,
      long netMinor,
      long openPriceMinor) {}
}
