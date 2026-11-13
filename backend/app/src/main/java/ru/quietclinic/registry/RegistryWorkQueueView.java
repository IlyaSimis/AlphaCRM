package ru.quietclinic.registry;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Действия администратора на день; клинический текст и контакты пациента в очередь не входят. */
record RegistryWorkQueueView(LocalDate date, List<Item> items) {
  record Item(
      String id,
      String kind,
      UUID appointmentId,
      UUID patientId,
      String patientName,
      UUID practitionerId,
      String practitionerName,
      Instant startsAt,
      String serviceName,
      String appointmentStatus,
      UUID chargeId,
      Long amountMinor,
      String currency,
      String reason) {}
}
