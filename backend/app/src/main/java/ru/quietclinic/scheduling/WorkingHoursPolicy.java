package ru.quietclinic.scheduling;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Запись должна целиком лежать внутри смены врача в этот день недели. Время — в той же шкале, что и
 * расписание: UTC, ISO-день недели (понедельник = 1).
 */
final class WorkingHoursPolicy {
  private WorkingHoursPolicy() {}

  static boolean fits(List<PractitionerSchedule> shifts, Instant startsAt, Instant endsAt) {
    var start = LocalDateTime.ofInstant(startsAt, ZoneOffset.UTC);
    var end = LocalDateTime.ofInstant(endsAt, ZoneOffset.UTC);
    if (!end.isAfter(start) || !end.toLocalDate().equals(start.toLocalDate())) return false;
    var day = start.getDayOfWeek().getValue();
    return shifts.stream()
        .anyMatch(
            shift ->
                shift.dayOfWeek == day
                    && !start.toLocalTime().isBefore(shift.startsAt)
                    && !end.toLocalTime().isAfter(shift.endsAt));
  }
}
