package ru.quietclinic.insights;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Отчёт по снимкам услуг; детализация содержит только административные данные приёма. */
record ServiceAnalyticsView(
    LocalDate from,
    LocalDate to,
    LocalDate previousFrom,
    LocalDate previousTo,
    String currency,
    Totals totals,
    Totals previousTotals,
    List<Row> rows) {
  record Totals(
      long appointments,
      long visits,
      long uniquePatients,
      long newPatients,
      long returningPatients,
      long noShow,
      long cancelled,
      long withoutNextVisit,
      long revenueMinor,
      long payments,
      Long averageCheckMinor) {}

  record Row(
      String serviceKey,
      String serviceName,
      long appointments,
      long visits,
      long uniquePatients,
      long newPatients,
      long returningPatients,
      long noShow,
      long cancelled,
      long withoutNextVisit,
      long revenueMinor,
      long payments,
      Long averageCheckMinor,
      long previousVisits,
      long previousRevenueMinor) {}

  record Details(LocalDate from, LocalDate to, String serviceKey, String serviceName, List<Appointment> appointments) {}

  record Appointment(
      UUID appointmentId,
      UUID patientId,
      String patientName,
      UUID practitionerId,
      String practitionerName,
      Instant startsAt,
      String status,
      UUID chargeId,
      String chargeStatus,
      Long amountMinor,
      String currency,
      boolean withoutNextVisit) {}
}
