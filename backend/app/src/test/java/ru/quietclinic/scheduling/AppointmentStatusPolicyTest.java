package ru.quietclinic.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AppointmentStatusPolicyTest {
  @ParameterizedTest
  @CsvSource({
    "BOOKED, CONFIRMED",
    "BOOKED, ARRIVED",
    "BOOKED, NO_SHOW",
    "CONFIRMED, ARRIVED",
    "CONFIRMED, NO_SHOW",
    "BOOKED, IN_PROGRESS",
    "CONFIRMED, IN_PROGRESS",
    "ARRIVED, IN_PROGRESS",
    "ARRIVED, COMPLETED",
    "IN_PROGRESS, COMPLETED"
  })
  void allowsVisitLifecycleTransitions(String from, String to) {
    assertThat(AppointmentStatusPolicy.allowed(from, to)).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "BOOKED, COMPLETED",
    "CONFIRMED, COMPLETED",
    "COMPLETED, IN_PROGRESS",
    "NO_SHOW, IN_PROGRESS",
    "CANCELLED, IN_PROGRESS",
    "CONFIRMED, CONFIRMED",
    "ARRIVED, NO_SHOW",
    "COMPLETED, ARRIVED",
    "NO_SHOW, ARRIVED",
    "CANCELLED, CONFIRMED",
    "UNKNOWN, CONFIRMED"
  })
  void rejectsOtherTransitions(String from, String to) {
    assertThat(AppointmentStatusPolicy.allowed(from, to)).isFalse();
  }

  @Test
  void splitsTargetsBetweenAdministratorAndDoctor() {
    assertThat(AppointmentStatusPolicy.administratorMay("CONFIRMED")).isTrue();
    assertThat(AppointmentStatusPolicy.administratorMay("NO_SHOW")).isTrue();
    assertThat(AppointmentStatusPolicy.administratorMay("COMPLETED")).isFalse();
    assertThat(AppointmentStatusPolicy.doctorMay("COMPLETED")).isTrue();
    assertThat(AppointmentStatusPolicy.doctorMay("NO_SHOW")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"COMPLETED", "NO_SHOW", "CANCELLED"})
  void finalStatusesAreNotActive(String status) {
    assertThat(AppointmentStatusPolicy.ACTIVE).doesNotContain(status);
  }
}
