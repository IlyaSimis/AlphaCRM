package ru.quietclinic.scheduling;

import java.util.UUID;

record ServiceView(UUID id, String name, int durationMinutes, long priceMinor, String currency) {

  static ServiceView of(ClinicService s) {
    return new ServiceView(s.id, s.name, s.durationMinutes, s.priceMinor, s.currency);
  }
}
