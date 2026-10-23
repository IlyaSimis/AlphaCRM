package ru.quietclinic.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Услуга клиники из справочника: длительность и цена в копейках. Услугу не удаляют, а выключают:
 * выключенная не предлагается в новой записи, старые записи хранят свой снимок.
 */
@Entity
@Table(name = "clinic_service")
class ClinicService {
  @Id UUID id;

  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  String name;

  @Column(nullable = false)
  int durationMinutes;

  @Column(nullable = false)
  long priceMinor;

  @Column(nullable = false)
  String currency;

  @Column(nullable = false)
  boolean active;

  protected ClinicService() {}

  ClinicService(UUID clinicId, String name, int durationMinutes, long priceMinor, String currency) {
    id = UUID.randomUUID();
    this.clinicId = clinicId;
    this.name = name;
    this.durationMinutes = durationMinutes;
    this.priceMinor = priceMinor;
    this.currency = currency;
    active = true;
  }
}
