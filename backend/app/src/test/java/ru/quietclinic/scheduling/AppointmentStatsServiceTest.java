package ru.quietclinic.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppointmentStatsServiceTest {
  static final UUID PETROV = UUID.fromString("00000000-0000-0000-0000-000000000201");
  static final UUID LEBEDEVA = UUID.fromString("00000000-0000-0000-0000-000000000202");

  private static Appointment appointment(UUID practitioner, String startsAt, String status) {
    var start = Instant.parse(startsAt);
    var appointment =
        new Appointment(UUID.randomUUID(), practitioner, start, start.plusSeconds(1800), "Приём");
    appointment.status = status;
    return appointment;
  }

  @Test
  void groupsByUtcDayPractitionerAndStatus() {
    var rows =
        AppointmentStatsService.aggregate(
            List.of(
                appointment(PETROV, "2026-10-06T09:00:00Z", "COMPLETED"),
                appointment(PETROV, "2026-10-06T10:00:00Z", "COMPLETED"),
                appointment(LEBEDEVA, "2026-10-06T11:00:00Z", "NO_SHOW"),
                appointment(PETROV, "2026-10-06T23:30:00Z", "CANCELLED"),
                appointment(PETROV, "2026-10-07T00:10:00Z", "COMPLETED")));

    assertThat(rows)
        .containsExactly(
            new AppointmentStatRow(LocalDate.parse("2026-10-06"), PETROV, "CANCELLED", 1),
            new AppointmentStatRow(LocalDate.parse("2026-10-06"), PETROV, "COMPLETED", 2),
            new AppointmentStatRow(LocalDate.parse("2026-10-06"), LEBEDEVA, "NO_SHOW", 1),
            new AppointmentStatRow(LocalDate.parse("2026-10-07"), PETROV, "COMPLETED", 1));
  }

  @Test
  void emptyInputGivesNoRows() {
    assertThat(AppointmentStatsService.aggregate(List.of())).isEmpty();
  }
}
