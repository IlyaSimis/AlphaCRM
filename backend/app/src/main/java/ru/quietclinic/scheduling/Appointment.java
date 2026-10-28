package ru.quietclinic.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment")
class Appointment {
  @Id UUID id;

  @Column(nullable = false)
  UUID patientId;

  @Column(nullable = false)
  UUID practitionerId;

  @Column(nullable = false)
  Instant startsAt;

  @Column(nullable = false)
  Instant endsAt;

  @Column(nullable = false)
  String serviceName;

  @Column(nullable = false)
  String status;

  @Column(nullable = false)
  Instant createdAt;

  /** Услуга из справочника; у старых записей может быть пустой. */
  UUID serviceId;

  /** Снимок режима цены: FIXED или OPEN (свободная цена); у старых записей без цены — пусто. */
  String priceMode;

  /** Снимок цены услуги на момент записи; при свободной цене пуст. */
  Long amountMinor;

  /** Предварительная оценка свободной цены (необязательна). */
  Long estimateMinor;

  String currency;

  @Version long version;

  protected Appointment() {}

  Appointment(
      UUID patientId, UUID practitionerId, Instant startsAt, Instant endsAt, String serviceName) {
    this(patientId, practitionerId, startsAt, endsAt, serviceName, null, null);
  }

  Appointment(
      UUID patientId,
      UUID practitionerId,
      Instant startsAt,
      Instant endsAt,
      String serviceName,
      Long amountMinor,
      String currency) {
    id = UUID.randomUUID();
    this.patientId = patientId;
    this.practitionerId = practitionerId;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.serviceName = serviceName;
    this.amountMinor = amountMinor;
    this.currency = currency;
    if (amountMinor != null) priceMode = "FIXED";
    status = "BOOKED";
    createdAt = Instant.now();
  }
}
