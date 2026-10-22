package ru.quietclinic.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "charge")
class Charge {
  @Id UUID id;

  @Column(nullable = false)
  UUID appointmentId;

  @Column(nullable = false)
  long amountMinor;

  @Column(nullable = false)
  String currency;

  @Column(nullable = false)
  String status;

  @Column(nullable = false)
  Instant createdAt;

  protected Charge() {}

  Charge(UUID appointmentId, long amountMinor, String currency) {
    id = UUID.randomUUID();
    this.appointmentId = appointmentId;
    this.amountMinor = amountMinor;
    this.currency = currency;
    status = "PENDING";
    createdAt = Instant.now();
  }
}
