package ru.quietclinic.scheduling;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Запись должна целиком лежать внутри смены врача в этот день недели. Время — в той же шкале, что и
 * смены: часовой пояс клиники, ISO-день недели (понедельник = 1).
 */
final class WorkingHoursPolicy {
  private WorkingHoursPolicy() {}

  static boolean fits(
      List<PractitionerSchedule> shifts, Instant startsAt, Instant endsAt, ZoneId zone) {
    LocalDateTime start = LocalDateTime.ofInstant(startsAt, zone);
    LocalDateTime end = LocalDateTime.ofInstant(endsAt, zone);
    if (!end.isAfter(start) || !end.toLocalDate().equals(start.toLocalDate())) return false;
    int day = start.getDayOfWeek().getValue();
    return shifts.stream()
        .anyMatch(
            shift ->
                shift.dayOfWeek == day
                    && !start.toLocalTime().isBefore(shift.startsAt)
                    && !end.toLocalTime().isAfter(shift.endsAt));
  }
}
