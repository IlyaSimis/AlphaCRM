package ru.quietclinic.billing;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.communications.OutboxService;
import ru.quietclinic.platform.DomainEvents;
import ru.quietclinic.platform.Permission;

/** Асинхронная оплата терминалом: PAID ставит только подписанный ответ SUCCEEDED. */
@Service
class TerminalPaymentService {
  private static final UUID TERMINAL_ACTOR =
      UUID.fromString("00000000-0000-0000-0000-0000000007e1");
  private static final Set<String> FINAL_STATUSES =
      Set.of("SUCCEEDED", "CANCELLED", "FAILED", "TIMEOUT");

  private final ChargeRepository charges;
  private final PaymentRepository payments;
  private final ChargeService chargeService;
  private final TerminalPaymentGateway gateway;
  private final OutboxService outbox;
  private final AuditService audit;
  private final DomainEvents events;
  private final JdbcTemplate jdbc;
  private final String provider;

  TerminalPaymentService(
      ChargeRepository charges,
      PaymentRepository payments,
      ChargeService chargeService,
      TerminalPaymentGateway gateway,
      OutboxService outbox,
      AuditService audit,
      DomainEvents events,
      JdbcTemplate jdbc,
      @Value("${clinic.terminal.provider:generic}") String provider) {
    this.charges = charges;
    this.payments = payments;
    this.chargeService = chargeService;
    this.gateway = gateway;
    this.outbox = outbox;
    this.audit = audit;
    this.events = events;
    this.jdbc = jdbc;
    this.provider = provider;
  }

  @Transactional
  BillingView start(UUID chargeId, TerminalPaymentRequest request) {
    requirePermission(Permission.INVOICE_WRITE);
    Optional<PaymentRecord> existing = payments.findByIdempotencyKey(request.idempotencyKey());
    if (existing.isPresent()) {
      PaymentRecord payment = existing.get();
      if (!payment.chargeId.equals(chargeId) || !request.method().name().equals(payment.method)) {
        throw conflict("IDEMPOTENCY_KEY_REUSED", "This key was used for another payment");
      }
      Charge charge =
          charges
              .findById(chargeId)
              .filter(chargeService::inClinic)
              .orElseThrow(() -> notFound("CHARGE_NOT_FOUND", "Charge not found"));
      return view(charge, payment);
    }
    if (!gateway.configured()) {
      throw conflict(
          "TERMINAL_NOT_CONFIGURED", "Connect the bank or payment terminal provider first");
    }
    Charge charge =
        charges
            .findLockedById(chargeId)
            .filter(chargeService::inClinic)
            .orElseThrow(() -> notFound("CHARGE_NOT_FOUND", "Charge not found"));
    if (Charge.AWAITING_AMOUNT.equals(charge.status)) {
      throw conflict("AMOUNT_REQUIRED", "Enter the amount before accepting payment");
    }
    if (Charge.VOIDED.equals(charge.status)) {
      throw conflict("CHARGE_VOIDED", "The charge is voided");
    }
    if (!Charge.PENDING.equals(charge.status)) {
      throw conflict("CHARGE_NOT_PAYABLE", "Charge is already paid");
    }
    Optional<PaymentRecord> latest = payments.findFirstByChargeIdOrderByCreatedAtDesc(charge.id);
    if (latest.isPresent() && "PENDING".equals(latest.get().paymentStatus)) {
      throw conflict("TERMINAL_PAYMENT_PENDING", "A terminal payment is already in progress");
    }
    Instant now = Instant.now();
    PaymentRecord payment = new PaymentRecord();
    payment.id = UUID.randomUUID();
    payment.idempotencyKey = request.idempotencyKey();
    payment.chargeId = charge.id;
    payment.providerRef = "alphacrm-" + payment.id;
    payment.paymentStatus = "PENDING";
    payment.fiscalStatus = "PENDING";
    payment.method = request.method().name();
    payment.createdAt = now;
    payment.updatedAt = now;
    payments.saveAndFlush(payment);
    String reference =
        gateway.start(
            new TerminalPaymentCommand(
                payment.id,
                charge.id,
                charge.amountMinor,
                charge.currency,
                request.method(),
                request.idempotencyKey()));
    if (reference == null || reference.isBlank() || reference.length() > 255) {
      throw conflict("TERMINAL_RESPONSE_INVALID", "Terminal returned no payment reference");
    }
    payment.providerRef = reference.trim();
    payments.saveAndFlush(payment);
    audit.record(
        actorId(),
        "TERMINAL_PAYMENT_REQUESTED",
        "PAYMENT",
        payment.id,
        "ALLOWED",
        request.method().name());
    return view(charge, payment);
  }

  @Transactional
  TerminalEventView event(TerminalEventRequest request) {
    PaymentRecord payment =
        payments
            .findLockedByProviderRef(request.paymentRef())
            .orElseThrow(
                () -> notFound("TERMINAL_PAYMENT_NOT_FOUND", "Terminal payment not found"));
    Integer duplicate =
        jdbc.queryForObject(
            "select count(*) from terminal_payment_event where provider = ? and event_id = ?",
            Integer.class,
            provider,
            request.eventId());
    if (duplicate != null && duplicate > 0) {
      return new TerminalEventView(true, true, payment.paymentStatus);
    }
    jdbc.update(
        "insert into terminal_payment_event(id, provider, event_id, payment_id, received_at)"
            + " values(?,?,?,?,?)",
        UUID.randomUUID(),
        provider,
        request.eventId(),
        payment.id,
        Timestamp.from(Instant.now()));
    if (!List.of("CARD", "SBP").contains(payment.method)) {
      throw conflict("NOT_TERMINAL_PAYMENT", "This payment was not sent to a terminal");
    }
    if (FINAL_STATUSES.contains(payment.paymentStatus)) {
      // Запоздавшее PENDING или повтор финального ответа не откатывает завершённую операцию.
      return new TerminalEventView(true, false, payment.paymentStatus);
    }
    Charge charge =
        charges
            .findLockedById(payment.chargeId)
            .orElseThrow(() -> notFound("CHARGE_NOT_FOUND", "Charge not found"));
    payment.updatedAt = Instant.now();
    if ("PENDING".equals(request.status())) {
      payments.saveAndFlush(payment);
      return new TerminalEventView(true, false, payment.paymentStatus);
    }
    payment.paymentStatus = request.status();
    payment.completedAt = payment.updatedAt;
    payment.failureReason = "SUCCEEDED".equals(request.status()) ? null : request.failureReason();
    if ("SUCCEEDED".equals(request.status())) {
      if (!Charge.PENDING.equals(charge.status)) {
        throw conflict("CHARGE_NOT_PAYABLE", "Charge cannot accept this terminal result");
      }
      payment.fiscalStatus = "QUEUED";
      charge.status = Charge.PAID;
      charges.saveAndFlush(charge);
      outbox.fiscalReceipt(payment.id, charge.appointmentId);
      UUID clinicId = clinicFor(charge);
      audit.record(
          TERMINAL_ACTOR, "PAYMENT_CONFIRMED", "PAYMENT", payment.id, "ALLOWED", "terminal");
      events.publish(
          "InvoicePaid",
          clinicId,
          TERMINAL_ACTOR,
          Map.of(
              "chargeId", charge.id,
              "paymentId", payment.id,
              "appointmentId", charge.appointmentId,
              "amountMinor", charge.amountMinor,
              "currency", charge.currency,
              "priceMode", charge.priceMode));
    } else {
      payment.fiscalStatus = "NOT_REQUIRED";
      audit.record(
          TERMINAL_ACTOR,
          "TERMINAL_PAYMENT_FINISHED",
          "PAYMENT",
          payment.id,
          "ALLOWED",
          request.status());
    }
    payments.saveAndFlush(payment);
    return new TerminalEventView(true, false, payment.paymentStatus);
  }

  private UUID clinicFor(Charge charge) {
    return jdbc.queryForObject(
        "select p.clinic_id from appointment a join practitioner p on p.id = a.practitioner_id"
            + " where a.id = ?",
        UUID.class,
        charge.appointmentId);
  }

  private static BillingView view(Charge charge, PaymentRecord payment) {
    return new BillingView(
        charge.id,
        charge.amountMinor,
        charge.currency,
        charge.status,
        payment.id,
        payment.paymentStatus,
        payment.fiscalStatus,
        payment.method,
        payment.failureReason);
  }
}
