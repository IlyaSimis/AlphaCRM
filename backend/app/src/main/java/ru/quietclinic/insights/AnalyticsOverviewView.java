package ru.quietclinic.insights;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Агрегированные показатели клиники. В ответе нет пациентов, услуг из записей или клинического
 * текста: только числа, даты, врачи, кабинеты и специальности. Суммы — в копейках.
 */
record AnalyticsOverviewView(
    LocalDate from,
    LocalDate to,
    String currency,
    Totals totals,
    List<Week> weeks,
    List<Load> practitioners,
    List<Load> rooms,
    List<NextSlot> nextSlots,
    Funnel funnel,
    Queues queues) {

  record Totals(
      long appointments,
      long attended,
      long noShow,
      long cancelled,
      long revenueMinor,
      long payments,
      Long averageCheckMinor,
      long debtMinor,
      long unpaidCharges,
      long openPriceMinor,
      long refundedMinor,
      long newPatients,
      long returningPatients,
      long completedPatients,
      long withoutNextVisit,
      long occupiedMinutes,
      long availableMinutes,
      long waitlistActive,
      long waitlistBooked,
      long waitlistPeriodTotal) {}

  record Week(
      LocalDate weekStart,
      long appointments,
      long attended,
      long noShow,
      long cancelled,
      long revenueMinor,
      long occupiedMinutes,
      long availableMinutes) {}

  record Load(
      String id, String label, String specialty, long occupiedMinutes, long availableMinutes) {}

  /** Ближайший свободный получасовой слот; среднее — по врачам этой специальности. */
  record NextSlot(
      String specialty, long practitioners, double averageHours, Instant nextAvailableAt) {}

  /** Когорта пациентов, созданных в периоде: карточка → запись → визит → оплата. */
  record Funnel(long contacts, long booked, long visited, long paid) {}

  record Queues(long unclosedEncounters, long unsignedDocuments, long unpaidCharges) {}
}
