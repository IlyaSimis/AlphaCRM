package ru.quietclinic.billing;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.communications.OutboxService;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {
  private final ChargeRepository charges;
  private final PaymentRepository payments;
  private final PaymentGateway gateway;
  private final OutboxService outbox;
  private final AuditService audit;
  private final ChargeService chargeService;

  BillingController(
      ChargeRepository charges,
      PaymentRepository payments,
      PaymentGateway gateway,
      OutboxService outbox,
      AuditService audit,
      ChargeService chargeService) {
    this.charges = charges;
    this.payments = payments;
    this.gateway = gateway;
    this.outbox = outbox;
    this.audit = audit;
    this.chargeService = chargeService;
  }

  @GetMapping("/charges")
  List<ChargeView> charges() {
    return chargeService.list();
  }

  @PostMapping("/charges")
  @ResponseStatus(HttpStatus.CREATED)
  ChargeView createCharge(@Valid @RequestBody ChargeRequest request) {
    return chargeService.create(request);
  }

  @PostMapping("/charges/{chargeId}/payments")
  @Transactional
  BillingView pay(@PathVariable UUID chargeId, @Valid @RequestBody PaymentRequest request) {
    requireRole("CASHIER");
    var charge =
        charges
            .findById(chargeId)
            .filter(chargeService::inClinic)
            .orElseThrow(() -> notFound("CHARGE_NOT_FOUND", "Charge not found"));
    var existing = payments.findByIdempotencyKey(request.idempotencyKey());
    if (existing.isPresent()) return view(charge, existing.get());
    if (!charge.status.equals("PENDING")) {
      throw conflict("CHARGE_NOT_PAYABLE", "Charge is already paid");
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
    // Чек уходит доставщиком outbox через порт кассы; пока касса не подключена — QUEUED.
    outbox.fiscalReceipt(payment.id, charge.appointmentId);
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
