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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Агрегаты по записям без персональных данных: только дата, врач, статус и количество. */
@Service
class AppointmentStatsService {
  static final int MAX_RANGE_DAYS = 366;

  private final AppointmentRepository appointments;

  AppointmentStatsService(AppointmentRepository appointments) {
    this.appointments = appointments;
  }

  @Transactional(readOnly = true)
  AppointmentStatsView stats(LocalDate from, LocalDate to, UUID practitionerId) {
    requireRole("MANAGER", "ADMINISTRATOR");
    if (to.isBefore(from)) {
      throw conflict("INVALID_RANGE", "Конец периода должен быть не раньше начала");
    }
    if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
      throw conflict("RANGE_TOO_LONG", "Период не может быть длиннее года");
    }
    var inRange =
        appointments.findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
            from.atStartOfDay().toInstant(ZoneOffset.UTC),
            to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
    var filtered =
        inRange.stream()
            .filter(a -> practitionerId == null || a.practitionerId.equals(practitionerId))
            .toList();
    return new AppointmentStatsView(from, to, aggregate(filtered));
  }

  /** Группирует записи по дню (UTC), врачу и статусу. */
  static List<AppointmentStatRow> aggregate(List<Appointment> appointments) {
    return appointments.stream()
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
  }

  private record Key(LocalDate date, UUID practitionerId, String status) {}
}
