package ru.quietclinic.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WaitlistEntryTest {
  static final UUID DOCTOR = UUID.randomUUID();

  @Test
  void matchesFreedSlotOfSameDoctorInsideWishedPeriod() {
    var entry =
        new WaitlistEntry(
            UUID.randomUUID(),
            DOCTOR,
            UUID.randomUUID(),
            LocalDate.parse("2026-10-19"),
            LocalDate.parse("2026-10-23"));

    assertThat(entry.matches(DOCTOR, LocalDate.parse("2026-10-19"))).isTrue();
    assertThat(entry.matches(DOCTOR, LocalDate.parse("2026-10-23"))).isTrue();
    assertThat(entry.matches(DOCTOR, LocalDate.parse("2026-10-24"))).isFalse();
    assertThat(entry.matches(UUID.randomUUID(), LocalDate.parse("2026-10-20"))).isFalse();
    entry.status = "BOOKED";
    assertThat(entry.matches(DOCTOR, LocalDate.parse("2026-10-20"))).isFalse();
  }
}
