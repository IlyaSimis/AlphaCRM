package ru.quietclinic.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "practitioner")
class Practitioner {
  @Id UUID id;

  /** Клиника врача: расписание и записи видны только сотрудникам этой клиники. */
  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  String fullName;

  @Column(nullable = false)
  String specialty;

  /** Кабинет — подпись для экрана, не ресурс: двое врачей могут делить один кабинет. */
  @Column(length = 80)
  String room;

  protected Practitioner() {}

  Practitioner(UUID clinicId, String fullName, String specialty, String room) {
    id = UUID.randomUUID();
    this.clinicId = clinicId;
    this.fullName = fullName;
    this.specialty = specialty;
    this.room = room;
  }

  /** Пустая строка кабинета хранится как NULL. */
  static String roomOrNull(String room) {
    return room == null || room.isBlank() ? null : room.trim();
  }
}
