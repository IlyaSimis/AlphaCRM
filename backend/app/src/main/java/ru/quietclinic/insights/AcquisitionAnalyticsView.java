package ru.quietclinic.insights;

import java.time.LocalDate;
import java.util.List;

/** Источники новых пациентов в CRM и внешняя веб-аналитика за тот же период. */
record AcquisitionAnalyticsView(
    LocalDate from,
    LocalDate to,
    String currency,
    List<ReferralSource> referralSources,
    WebAnalyticsPort.Report web) {

  /** Когорта карточек, созданных в периоде; выручка — накопленная по этой когорте. */
  record ReferralSource(
      String source,
      long contacts,
      long booked,
      long visited,
      long paid,
      long revenueMinor) {}
}
