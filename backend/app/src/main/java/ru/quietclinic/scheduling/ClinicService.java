package ru.quietclinic.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Услуга клиники из справочника: длительность и цена в копейках. */
@Entity
@Table(name = "clinic_service")
class ClinicService {
  @Id UUID id;

  @Column(nullable = false, unique = true)
  String name;

  @Column(nullable = false)
  int durationMinutes;

  @Column(nullable = false)
  long priceMinor;

  @Column(nullable = false)
  String currency;

  protected ClinicService() {}
}
