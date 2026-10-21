package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Количество записей за день по врачу и статусу. Даты считаются в UTC, как и расписание. */
record AppointmentStatRow(LocalDate date, UUID practitionerId, String status, long count) {}

record AppointmentStatsView(LocalDate from, LocalDate to, List<AppointmentStatRow> rows) {}

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
  static final int MAX_RANGE_DAYS = 366;

  private final AppointmentRepository appointments;

  AnalyticsController(AppointmentRepository appointments) {
    this.appointments = appointments;
  }

  /** Агрегаты без персональных данных: только дата, врач, статус и количество. */
  @GetMapping("/appointments")
  @Transactional(readOnly = true)
  AppointmentStatsView appointments(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) UUID practitionerId) {
    requireRole("MANAGER", "ADMINISTRATOR");
    if (to.isBefore(from)) {
      throw conflict("INVALID_RANGE", "Конец периода должен быть не раньше начала");
    }
    if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
      throw conflict("RANGE_TOO_LONG", "Период не может быть длиннее года");
    }
    var rows =
        appointments
            .findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))
            .stream()
            .filter(a -> practitionerId == null || a.practitionerId.equals(practitionerId))
            .collect(
                Collectors.groupingBy(
                    a ->
                        new Key(
                            LocalDate.ofInstant(a.startsAt, ZoneOffset.UTC),
                            a.practitionerId,
                            a.status),
                    Collectors.counting()))
            .entrySet()
            .stream()
            .map(
                e ->
                    new AppointmentStatRow(
                        e.getKey().date(),
                        e.getKey().practitionerId(),
                        e.getKey().status(),
                        e.getValue()))
            .sorted(
                Comparator.comparing(AppointmentStatRow::date)
                    .thenComparing(r -> r.practitionerId().toString())
                    .thenComparing(AppointmentStatRow::status))
            .toList();
    return new AppointmentStatsView(from, to, rows);
  }

  private record Key(LocalDate date, UUID practitionerId, String status) {}
}
