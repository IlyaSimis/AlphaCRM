package ru.quietclinic.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Норма расхода услуги: позиция склада и количество на один приём. Склад получает нормы в событии
 * {@code AppointmentCompleted} и проводит по ним расход; позиции склада расписание знает только по
 * id — без зависимости от кода и таблиц склада.
 */
@Entity
@Table(name = "clinic_service_norm")
class ServiceNorm {
  @Id UUID id;

  @Column(nullable = false)
  UUID serviceId;

  @Column(nullable = false)
  UUID itemId;

  @Column(nullable = false)
  BigDecimal quantity;

  protected ServiceNorm() {}

  ServiceNorm(UUID serviceId, UUID itemId, BigDecimal quantity) {
    id = UUID.randomUUID();
    this.serviceId = serviceId;
    this.itemId = itemId;
    this.quantity = quantity;
  }
}
