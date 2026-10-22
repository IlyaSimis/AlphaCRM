package ru.quietclinic.scheduling;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Код основания переноса для аудита: прежние и новые дата и время (UTC, как в расписании), без
 * имени пациента. Пример: {@code 2026-10-20T09:00Z>2026-10-22T10:30Z;practitioner}.
 */
final class RescheduleReason {
  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'").withZone(ZoneOffset.UTC);

  private RescheduleReason() {}

  static String code(Instant from, Instant to, boolean practitionerChanged) {
    var code = FORMAT.format(from) + ">" + FORMAT.format(to);
    return practitionerChanged ? code + ";practitioner" : code;
  }
}
