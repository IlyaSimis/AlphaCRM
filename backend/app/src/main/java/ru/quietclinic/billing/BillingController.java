package ru.quietclinic.billing;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.communications.OutboxService;

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

interface ChargeRepository extends JpaRepository<Charge, UUID> {}

interface PaymentRepository extends JpaRepository<PaymentRecord, UUID> {
  java.util.Optional<PaymentRecord> findByIdempotencyKey(String key);
}

interface PaymentGateway {
  ProviderPayment capture(UUID chargeId, long amountMinor, String currency, String idempotencyKey);
}

record ProviderPayment(String reference, String status) {}

@Component
class SafeMockPaymentGateway implements PaymentGateway {
  public ProviderPayment capture(
      UUID chargeId, long amountMinor, String currency, String idempotencyKey) {
    return new ProviderPayment("mock-pay-" + chargeId, "CONFIRMED");
  }
}

record ChargeRequest(
    @NotNull UUID appointmentId, @Positive long amountMinor, @NotBlank String currency) {}

record PaymentRequest(@NotBlank String idempotencyKey) {}

record BillingView(
    UUID chargeId,
    long amountMinor,
    String currency,
    String chargeStatus,
    UUID paymentId,
    String paymentStatus,
    String fiscalStatus) {}

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {
  private final ChargeRepository charges;
  private final PaymentRepository payments;
  private final PaymentGateway gateway;
  private final OutboxService outbox;
  private final AuditService audit;

  BillingController(
      ChargeRepository charges,
      PaymentRepository payments,
      PaymentGateway gateway,
      OutboxService outbox,
      AuditService audit) {
    this.charges = charges;
    this.payments = payments;
    this.gateway = gateway;
    this.outbox = outbox;
    this.audit = audit;
  }

  @PostMapping("/charges")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  BillingView createCharge(@Valid @RequestBody ChargeRequest request) {
    requireRole("CASHIER", "REGISTRAR");
    var charge =
        charges.save(
            new Charge(request.appointmentId(), request.amountMinor(), request.currency()));
    audit.record(actorId(), "CHARGE_CREATED", "CHARGE", charge.id, "ALLOWED", "billing");
    return view(charge, null);
  }

  @PostMapping("/charges/{chargeId}/payments")
  @Transactional
  BillingView pay(@PathVariable UUID chargeId, @Valid @RequestBody PaymentRequest request) {
    requireRole("CASHIER");
    var charge = charges.findById(chargeId).orElseThrow();
    var existing = payments.findByIdempotencyKey(request.idempotencyKey());
    if (existing.isPresent()) return view(charge, existing.get());
    if (!charge.status.equals("PENDING")) {
      throw conflict("CHARGE_NOT_PAYABLE", "Начисление уже оплачено");
    }
    var provider =
        gateway.capture(charge.id, charge.amountMinor, charge.currency, request.idempotencyKey());
    var payment = new PaymentRecord();
    payment.id = UUID.randomUUID();
    payment.idempotencyKey = request.idempotencyKey();
    payment.chargeId = charge.id;
    payment.providerRef = provider.reference();
    payment.paymentStatus = provider.status();
    payment.fiscalStatus = "QUEUED";
    payment.createdAt = Instant.now();
    payments.save(payment);
    charge.status = "PAID";
    outbox.enqueue(
        "FISCALIZATION", "PAYMENT", payment.id, "{\"operation\":\"receipt\",\"safe\":true}");
    audit.record(actorId(), "PAYMENT_CONFIRMED", "PAYMENT", payment.id, "ALLOWED", "billing");
    return view(charge, payment);
  }

  private BillingView view(Charge charge, PaymentRecord payment) {
    return new BillingView(
        charge.id,
        charge.amountMinor,
        charge.currency,
        charge.status,
        payment == null ? null : payment.id,
        payment == null ? null : payment.paymentStatus,
        payment == null ? null : payment.fiscalStatus);
  }
}
