package ru.quietclinic.registry;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

record TaskView(
    UUID id,
    UUID patientId,
    String title,
    LocalDate dueDate,
    UUID assigneeId,
    String status,
    Instant createdAt,
    Instant doneAt) {

  static TaskView of(RegistryTask t) {
    return new TaskView(
        t.id, t.patientId, t.title, t.dueDate, t.assigneeId, t.status, t.createdAt, t.doneAt);
  }
}
