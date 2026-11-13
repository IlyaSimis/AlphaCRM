package ru.quietclinic.scheduling;

import java.util.UUID;

/** priceMinor пуст у свободной цены (priceMode = OPEN): тогда заданы minMinor и maxMinor. */
record ServiceView(
    UUID id,
    String name,
    int durationMinutes,
    Long priceMinor,
    String currency,
    boolean active,
    String priceMode,
    Long minMinor,
    Long maxMinor) {

  static ServiceView of(ClinicService s) {
    return new ServiceView(
        s.id,
        s.name,
        s.durationMinutes,
        s.priceMinor,
        s.currency,
        s.active,
        s.priceMode,
        s.minMinor,
        s.maxMinor);
  }
}
