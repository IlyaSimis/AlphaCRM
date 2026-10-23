package ru.quietclinic.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Клиника. Вторую можно добавить строкой в таблицу и своим администратором — без правки кода. */
@Entity
@Table(name = "clinic")
class Clinic {
  @Id UUID id;

  @Column(nullable = false)
  String name;

  @Column(nullable = false)
  Instant createdAt;

  protected Clinic() {}

  Clinic(String name) {
    id = UUID.randomUUID();
    this.name = name;
    createdAt = Instant.now();
  }
}
