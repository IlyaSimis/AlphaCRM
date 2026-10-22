package ru.quietclinic.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_record")
class PaymentRecord {
  @Id UUID id;

  @Column(nullable = false, unique = true)
  String idempotencyKey;

  @Column(nullable = false)
  UUID chargeId;

  @Column(nullable = false)
  String providerRef;

  @Column(nullable = false)
  String paymentStatus;

  @Column(nullable = false)
  String fiscalStatus;

  @Column(nullable = false)
  Instant createdAt;

  protected PaymentRecord() {}
}
