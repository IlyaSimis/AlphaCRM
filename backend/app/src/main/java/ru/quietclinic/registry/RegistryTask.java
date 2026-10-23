package ru.quietclinic.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Задача регистратуры: свободный текст администратора со сроком и исполнителем, пациент — по
 * желанию. Сообщения пациенту не отправляет.
 */
@Entity
@Table(name = "registry_task")
class RegistryTask {
  @Id UUID id;

  @Column(nullable = false)
  UUID clinicId;

  /** Необязателен: задача без пациента видна в списке на сегодня, но не в карточке. */
  UUID patientId;

  @Column(nullable = false, length = 200)
  String title;

  @Column(nullable = false)
  LocalDate dueDate;

  @Column(nullable = false)
  UUID assigneeId;

  @Column(nullable = false)
  String status;

  @Column(nullable = false)
  UUID createdBy;

  @Column(nullable = false)
  Instant createdAt;

  Instant doneAt;

  protected RegistryTask() {}

  RegistryTask(
      UUID clinicId,
      UUID patientId,
      String title,
      LocalDate dueDate,
      UUID assigneeId,
      UUID createdBy) {
    id = UUID.randomUUID();
    this.clinicId = clinicId;
    this.patientId = patientId;
    this.title = title;
    this.dueDate = dueDate;
    this.assigneeId = assigneeId;
    this.createdBy = createdBy;
    status = "OPEN";
    createdAt = Instant.now();
  }
}
