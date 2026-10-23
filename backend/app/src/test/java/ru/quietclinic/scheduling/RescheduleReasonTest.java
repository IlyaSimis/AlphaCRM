package ru.quietclinic.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RescheduleReasonTest {
  @Test
  void keepsOnlyOldAndNewTimeInUtc() {
    assertThat(
            RescheduleReason.code(
                Instant.parse("2026-10-20T09:00:00Z"),
                Instant.parse("2026-10-22T10:30:00Z"),
                false))
        .isEqualTo("2026-10-20T09:00Z>2026-10-22T10:30Z");
  }

  @Test
  void marksPractitionerChange() {
    assertThat(
            RescheduleReason.code(
                Instant.parse("2026-10-20T09:00:00Z"), Instant.parse("2026-10-20T11:00:00Z"), true))
        .isEqualTo("2026-10-20T09:00Z>2026-10-20T11:00Z;practitioner");
  }
}
