package ru.quietclinic.billing;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.communications.OutboxService;
import ru.quietclinic.platform.DomainEvents;
import ru.quietclinic.platform.Permission;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {
  private final ChargeRepository charges;
  private final PaymentRepository payments;
  private final PaymentGateway gateway;
  private final OutboxService outbox;
  private final AuditService audit;
  private final ChargeService chargeService;
  private final FinanceService finance;
  private final DomainEvents events;

  BillingController(
      ChargeRepository charges,
      PaymentRepository payments,
      PaymentGateway gateway,
      OutboxService outbox,
      AuditService audit,
      ChargeService chargeService,
      FinanceService finance,
      DomainEvents events) {
    this.charges = charges;
    this.payments = payments;
    this.gateway = gateway;
    this.outbox = outbox;
    this.audit = audit;
    this.chargeService = chargeService;
    this.finance = finance;
    this.events = events;
  }

  /** Аннулировать неоплаченный счёт: код причины из списка. */
  @PostMapping("/charges/{chargeId}/void")
  ChargeView voidCharge(@PathVariable UUID chargeId, @Valid @RequestBody VoidRequest request) {
    return finance.voidCharge(chargeId, request);
  }

  /** Полный возврат оплаты с чеком возврата. */
  @PostMapping("/payments/{paymentId}/refunds")
  @ResponseStatus(HttpStatus.CREATED)
  RefundView refund(@PathVariable UUID paymentId, @Valid @RequestBody RefundRequest request) {
    return finance.refund(paymentId, request);
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

  /** Сумма свободной цены (счёт в статусе AWAITING_AMOUNT). */
  @PutMapping("/charges/{chargeId}/amount")
  ChargeView setAmount(
      @PathVariable UUID chargeId, @Valid @RequestBody ChargeAmountRequest request) {
    return chargeService.setAmount(chargeId, request);
  }

  @PostMapping("/charges/{chargeId}/payments")
  @Transactional
  BillingView pay(@PathVariable UUID chargeId, @Valid @RequestBody PaymentRequest request) {
    requirePermission(Permission.INVOICE_WRITE);
    Charge charge =
        charges
            .findById(chargeId)
            .filter(chargeService::inClinic)
            .orElseThrow(() -> notFound("CHARGE_NOT_FOUND", "Charge not found"));
    Optional<PaymentRecord> existing = payments.findByIdempotencyKey(request.idempotencyKey());
    if (existing.isPresent()) return view(charge, existing.get());
    // Свободная цена без суммы: оплатить нельзя, пока сумму не ввели.
    if (Charge.AWAITING_AMOUNT.equals(charge.status)) {
      throw conflict("AMOUNT_REQUIRED", "Enter the amount before accepting payment");
    }
    if (Charge.VOIDED.equals(charge.status)) {
      throw conflict("CHARGE_VOIDED", "The charge is voided");
    }
    if (!charge.status.equals("PENDING")) {
      throw conflict("CHARGE_NOT_PAYABLE", "Charge is already paid");
    }
    ProviderPayment provider =
        gateway.capture(charge.id, charge.amountMinor, charge.currency, request.idempotencyKey());
    PaymentRecord payment = new PaymentRecord();
    payment.id = UUID.randomUUID();
    payment.idempotencyKey = request.idempotencyKey();
    payment.chargeId = charge.id;
    payment.providerRef = provider.reference();
    payment.paymentStatus = provider.status();
    payment.fiscalStatus = "QUEUED";
    payment.method = "MANUAL";
    payment.createdAt = Instant.now();
    payment.updatedAt = payment.createdAt;
    payments.save(payment);
    charge.status = "PAID";
    // Чек уходит доставщиком outbox через порт кассы; пока касса не подключена — QUEUED.
    outbox.fiscalReceipt(payment.id, charge.appointmentId);
    audit.record(actorId(), "PAYMENT_CONFIRMED", "PAYMENT", payment.id, "ALLOWED", "billing");
    events.publish(
        "InvoicePaid",
        Map.of(
            "chargeId", charge.id,
            "paymentId", payment.id,
            "appointmentId", charge.appointmentId,
            "amountMinor", charge.amountMinor,
            "currency", charge.currency,
            "priceMode", charge.priceMode));
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
        payment == null ? null : payment.fiscalStatus,
        payment == null ? null : payment.method,
        payment == null ? null : payment.failureReason);
  }
}
