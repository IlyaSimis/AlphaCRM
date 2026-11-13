package ru.quietclinic.insights;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Дашборд сотрудника. Подписи виджетов выбирает интерфейс по коду; scope — OWN (свои приёмы) или
 * CLINIC (вся клиника).
 */
record DashboardView(
    String role, LocalDate monthStart, LocalDate today, Instant generatedAt, List<Widget> widgets) {

  /**
   * value — главное число; secondary — второе (например, «из них завершено»); unit — count, money
   * (копейки) или bytes; items — короткий список (времена ближайших приёмов) без персональных
   * данных.
   */
  record Widget(
      String code, String scope, long value, Long secondary, String unit, List<String> items) {}
}
