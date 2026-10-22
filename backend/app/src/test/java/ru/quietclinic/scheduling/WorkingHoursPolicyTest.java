package ru.quietclinic.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorkingHoursPolicyTest {
  static final UUID DOCTOR = UUID.randomUUID();
  // Вторник 09:00–18:00 и четверг 10:00–19:00 — как в seed.
  static final List<PractitionerSchedule> SHIFTS =
      List.of(
          new PractitionerSchedule(DOCTOR, 2, LocalTime.of(9, 0), LocalTime.of(18, 0)),
          new PractitionerSchedule(DOCTOR, 4, LocalTime.of(10, 0), LocalTime.of(19, 0)));

  private static boolean fits(String start, String end) {
    return WorkingHoursPolicy.fits(SHIFTS, Instant.parse(start), Instant.parse(end));
  }

  @ParameterizedTest
  @CsvSource({
    "2026-10-20T09:00:00Z, 2026-10-20T09:50:00Z",
    "2026-10-20T17:30:00Z, 2026-10-20T18:00:00Z",
    "2026-10-22T10:00:00Z, 2026-10-22T10:30:00Z",
    "2026-10-22T18:30:00Z, 2026-10-22T19:00:00Z"
  })
  void acceptsIntervalsInsideShift(String start, String end) {
    assertThat(fits(start, end)).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "2026-10-20T08:45:00Z, 2026-10-20T09:15:00Z",
    "2026-10-20T17:45:00Z, 2026-10-20T18:15:00Z",
    "2026-10-21T10:00:00Z, 2026-10-21T10:30:00Z",
    "2026-10-22T09:30:00Z, 2026-10-22T10:00:00Z",
    "2026-10-20T10:00:00Z, 2026-10-20T10:00:00Z",
    "2026-10-20T23:50:00Z, 2026-10-21T00:20:00Z"
  })
  void rejectsIntervalsOutsideShift(String start, String end) {
    assertThat(fits(start, end)).isFalse();
  }

  @Test
  void noShiftsMeansNoAppointments() {
    assertThat(
            WorkingHoursPolicy.fits(
                List.of(),
                Instant.parse("2026-10-20T10:00:00Z"),
                Instant.parse("2026-10-20T10:30:00Z")))
        .isFalse();
  }
}
