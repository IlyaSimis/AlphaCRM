package ru.quietclinic.platform;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Время клиники. Записи хранятся как моменты (UTC), а дни, смены врачей и время на экране — в
 * часовом поясе клиники {@code clinic.time-zone} (CLINIC_TIME_ZONE, по умолчанию UTC).
 * {@code clinic.fixed-time} задаёт «сейчас» для тестов, чтобы правило «не в прошлом» не зависело
 * от дня запуска.
 */
@Component
public class ClinicTime {
  private final ZoneId zone;
  private final Clock clock;

  ClinicTime(
      @Value("${clinic.time-zone:UTC}") String zone,
      @Value("${clinic.fixed-time:}") String fixedTime) {
    this.zone = ZoneId.of(zone);
    this.clock =
        fixedTime == null || fixedTime.isBlank()
            ? Clock.systemUTC()
            : Clock.fixed(Instant.parse(fixedTime), this.zone);
  }

  public ZoneId zone() {
    return zone;
  }

  public Instant now() {
    return clock.instant();
  }

  public LocalDate today() {
    return LocalDate.ofInstant(now(), zone);
  }

  /** Начало дня в поясе клиники. */
  public Instant startOf(LocalDate day) {
    return day.atStartOfDay(zone).toInstant();
  }
}
