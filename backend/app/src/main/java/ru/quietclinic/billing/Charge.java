package ru.quietclinic.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Счёт по приёму. Статусы: {@code AWAITING_AMOUNT} (свободная цена, суммы ещё нет) → {@code
 * PENDING} → {@code PAID} → {@code REFUNDED} (возврат); неоплаченный можно аннулировать — {@code
 * VOIDED}. Пределы свободной цены — снимок услуги на момент завершения приёма.
 */
@Entity
@Table(name = "charge")
class Charge {
  static final String AWAITING_AMOUNT = "AWAITING_AMOUNT";
  static final String PENDING = "PENDING";
  static final String PAID = "PAID";
  static final String VOIDED = "VOIDED";
  static final String REFUNDED = "REFUNDED";

  @Id UUID id;

  @Column(nullable = false)
  UUID appointmentId;

  Long amountMinor;

  @Column(nullable = false)
  String currency;

  @Column(nullable = false)
  String status;

  @Column(nullable = false)
  String priceMode;

  Long minMinor;

  Long maxMinor;

  Long estimateMinor;

  @Column(nullable = false)
  Instant createdAt;

  Instant voidedAt;

  UUID voidedBy;

  /** Машинный код причины аннулирования. */
  String voidReason;

  protected Charge() {}

  Charge(UUID appointmentId, long amountMinor, String currency) {
    id = UUID.randomUUID();
    this.appointmentId = appointmentId;
    this.amountMinor = amountMinor;
    this.currency = currency;
    priceMode = "FIXED";
    status = PENDING;
    createdAt = Instant.now();
  }

  /** Счёт по свободной цене: сумму введут позже в пределах min–max. */
  static Charge awaitingAmount(
      UUID appointmentId, String currency, Long minMinor, Long maxMinor, Long estimateMinor) {
    var charge = new Charge(appointmentId, 0, currency);
    charge.amountMinor = null;
    charge.priceMode = "OPEN";
    charge.status = AWAITING_AMOUNT;
    charge.minMinor = minMinor;
    charge.maxMinor = maxMinor;
    charge.estimateMinor = estimateMinor;
    return charge;
  }

  /** Сумма свободной цены; вне пределов — 409 AMOUNT_OUT_OF_RANGE. */
  void setAmount(long amount) {
    if (!"OPEN".equals(priceMode)) {
      throw ru.quietclinic.platform.PlatformConfig.conflict(
          "AMOUNT_FIXED", "The price of this charge is fixed");
    }
    if (!AWAITING_AMOUNT.equals(status) && !PENDING.equals(status)) {
      throw ru.quietclinic.platform.PlatformConfig.conflict(
          "CHARGE_NOT_PAYABLE", "Charge is already paid or closed");
    }
    if ((minMinor != null && amount < minMinor) || (maxMinor != null && amount > maxMinor)) {
      throw ru.quietclinic.platform.PlatformConfig.conflict(
          "AMOUNT_OUT_OF_RANGE", "The amount is outside the open price limits");
    }
    if (amount <= 0) {
      throw ru.quietclinic.platform.PlatformConfig.conflict(
          "AMOUNT_OUT_OF_RANGE", "The amount must be positive");
    }
    amountMinor = amount;
    status = PENDING;
  }

  /** Аннулировать можно только неоплаченный счёт; оплаченный закрывается возвратом. */
  void voidBy(UUID actor, String reason, Instant at) {
    if (PAID.equals(status) || REFUNDED.equals(status)) {
      throw ru.quietclinic.platform.PlatformConfig.conflict(
          "CHARGE_PAID", "A paid charge is closed by a refund, not voided");
    }
    if (VOIDED.equals(status)) {
      throw ru.quietclinic.platform.PlatformConfig.conflict(
          "CHARGE_VOIDED", "The charge is already voided");
    }
    status = VOIDED;
    voidedAt = at;
    voidedBy = actor;
    voidReason = reason;
  }
}
