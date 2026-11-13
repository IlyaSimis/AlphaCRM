package ru.quietclinic.insights;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Внешняя продуктовая аналитика без cookies, ClientID и других идентификаторов посетителей. */
public interface WebAnalyticsPort {
  Report load(UUID clinicId, LocalDate from, LocalDate to);

  record Report(
      String state,
      String detail,
      Traffic traffic,
      List<DailyPoint> daily,
      List<TrafficSource> sources) {
    public static Report unavailable(String state) {
      return new Report(state, null, null, List.of(), List.of());
    }

    public static Report error(String detail) {
      return new Report("ERROR", detail, null, List.of(), List.of());
    }
  }

  record Traffic(
      long visits,
      long users,
      long pageviews,
      double newVisitorsPercent,
      double bounceRate,
      double pageDepth,
      double averageVisitDurationSeconds,
      long goalReaches,
      double goalConversionRate) {}

  record DailyPoint(LocalDate date, long visits, long users, long goalReaches) {}

  record TrafficSource(String source, long visits, long users, long goalReaches) {}
}
