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

  protected Practitioner() {}
}
