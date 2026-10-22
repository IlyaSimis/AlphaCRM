package ru.quietclinic.scheduling;

import java.util.Map;
import java.util.Set;

/** Допустимые переходы статуса записи и роли, которым они разрешены. */
final class AppointmentStatusPolicy {
  static final Set<String> ACTIVE = Set.of("BOOKED", "CONFIRMED", "ARRIVED", "IN_PROGRESS");

  private static final Map<String, Set<String>> TRANSITIONS =
      Map.of(
          "BOOKED", Set.of("CONFIRMED", "ARRIVED", "NO_SHOW"),
          "CONFIRMED", Set.of("ARRIVED", "NO_SHOW"),
          "ARRIVED", Set.of("IN_PROGRESS", "COMPLETED"),
          "IN_PROGRESS", Set.of("COMPLETED"));

  private static final Set<String> ADMINISTRATOR_TARGETS =
      Set.of("CONFIRMED", "ARRIVED", "NO_SHOW");
  private static final Set<String> DOCTOR_TARGETS = Set.of("IN_PROGRESS", "COMPLETED");

  private AppointmentStatusPolicy() {}

  static boolean allowed(String from, String to) {
    return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
  }

  static boolean administratorMay(String to) {
    return ADMINISTRATOR_TARGETS.contains(to);
  }

  static boolean doctorMay(String to) {
    return DOCTOR_TARGETS.contains(to);
  }
}
