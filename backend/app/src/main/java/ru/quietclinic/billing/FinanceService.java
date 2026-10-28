package ru.quietclinic.billing;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.badRequest;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.communications.OutboxService;
import ru.quietclinic.platform.ClinicTime;
import ru.quietclinic.platform.DomainEvents;
import ru.quietclinic.platform.Permission;

/**
 * Финансы поверх счетов (раздел E, finance): аннулирование неоплаченного счёта, полный возврат
 * оплаты с чеком возврата и отчёт о продажах. Причины — коды из списка, без свободного текста.
 * События InvoiceVoided и RefundIssued — только идентификаторы и суммы.
 */
@Service
class FinanceService {
  /** Самый длинный период отчёта: год с запасом. */
  static final long MAX_REPORT_DAYS = 400;

  private final ChargeRepository charges;
  private final PaymentRepository payments;
  private final RefundRepository refunds;
  private final ChargeService chargeService;
  private final OutboxService outbox;
  private final AuditService audit;
  private final DomainEvents events;
  private final JdbcTemplate jdbc;
  private final ClinicTime time;

  FinanceService(
      ChargeRepository charges,
      PaymentRepository payments,
      RefundRepository refunds,
      ChargeService chargeService,
      OutboxService outbox,
      AuditService audit,
      DomainEvents events,
      JdbcTemplate jdbc,
      ClinicTime time) {
    this.charges = charges;
    this.payments = payments;
    this.refunds = refunds;
    this.chargeService = chargeService;
    this.outbox = outbox;
    this.audit = audit;
    this.events = events;
    this.jdbc = jdbc;
    this.time = time;
  }

  private Charge ownCharge(UUID chargeId) {
    return charges
        .findById(chargeId)
        .filter(chargeService::inClinic)
        .orElseThrow(() -> notFound("CHARGE_NOT_FOUND", "Charge not found"));
  }

  /** Аннулировать неоплаченный счёт (ошибка, дубль, отказ пациента). */
  @Transactional
  ChargeView voidCharge(UUID chargeId, VoidRequest request) {
    requirePermission(Permission.INVOICE_VOID);
    var charge = ownCharge(chargeId);
    charge.voidBy(actorId(), request.reason(), Instant.now());
    charges.saveAndFlush(charge);
    audit.record(actorId(), "CHARGE_VOIDED", "CHARGE", charge.id, "ALLOWED", request.reason());
    events.publish(
        "InvoiceVoided",
        Map.of(
            "chargeId",
            charge.id,
            "appointmentId",
            charge.appointmentId,
            "reason",
            request.reason()));
    return chargeService.view(charge);
  }

  /**
   * Полный возврат оплаты: счёт → REFUNDED, чек возврата в outbox (порт кассы). Повтор с тем же
   * ключом возвращает тот же возврат; второй возврат того же платежа — 409 ALREADY_REFUNDED.
   */
  @Transactional
  RefundView refund(UUID paymentId, RefundRequest request) {
    requirePermission(Permission.PAYMENT_REFUND);
    var existing = refunds.findByIdempotencyKey(request.idempotencyKey());
    if (existing.isPresent()) {
      if (!existing.get().paymentId.equals(paymentId)) {
        throw conflict("IDEMPOTENCY_KEY_REUSED", "This key was used for another refund");
      }
      return RefundView.of(existing.get());
    }
    var payment =
        payments
            .findById(paymentId)
            .orElseThrow(() -> notFound("PAYMENT_NOT_FOUND", "Payment not found"));
    var charge =
        charges
            .findById(payment.chargeId)
            .filter(chargeService::inClinic)
            .orElseThrow(() -> notFound("PAYMENT_NOT_FOUND", "Payment not found"));
    if (refunds.findByPaymentId(payment.id).isPresent() || Charge.REFUNDED.equals(charge.status)) {
      throw conflict("ALREADY_REFUNDED", "This payment is already refunded");
    }
    if (!Charge.PAID.equals(charge.status)) {
      throw conflict("CHARGE_NOT_PAID", "Only a paid charge can be refunded");
    }
    var refund =
        refunds.saveAndFlush(
            new RefundRecord(
                payment, charge, request.idempotencyKey(), request.reason(), actorId()));
    charge.status = Charge.REFUNDED;
    charges.saveAndFlush(charge);
    outbox.fiscalRefund(refund.id, charge.appointmentId);
    audit.record(actorId(), "PAYMENT_REFUNDED", "PAYMENT", payment.id, "ALLOWED", request.reason());
    events.publish(
        "RefundIssued",
        Map.of(
            "refundId", refund.id,
            "paymentId", payment.id,
            "chargeId", charge.id,
            "amountMinor", refund.amountMinor,
            "currency", refund.currency));
    return RefundView.of(refund);
  }

  /**
   * Продажи за период [from, to] по дням клиники, услугам или врачам: оплаты по дате оплаты,
   * возвраты по дате возврата (вычитаются). Без пациентов: только услуга, врач и суммы. Менеджер
   * видит то же — это агрегаты.
   */
  @Transactional(readOnly = true)
  SalesReport sales(LocalDate from, LocalDate to, String groupBy) {
    requirePermission(Permission.REVENUE_READ);
    if (from == null || to == null || to.isBefore(from)) {
      throw badRequest("INVALID_PERIOD", "The period must have a start before its end");
    }
    if (ChronoUnit.DAYS.between(from, to) > MAX_REPORT_DAYS) {
      throw badRequest("PERIOD_TOO_LONG", "The report period is limited to about a year");
    }
    var group = groupBy == null ? "day" : groupBy;
    if (!List.of("day", "service", "practitioner").contains(group)) {
      throw badRequest("INVALID_GROUP", "Group by day, service or practitioner");
    }
    var start = Timestamp.from(time.startOf(from));
    var end = Timestamp.from(time.startOf(to.plusDays(1)));
    var zone = time.zone();
    var rows = new LinkedHashMap<String, Row>();
    var paid =
        jdbc.queryForList(
            "select r.created_at as at, c.amount_minor as amount, c.price_mode as mode,"
                + " a.service_name as service, p.id as practitioner_id, p.full_name as practitioner"
                + " from payment_record r join charge c on c.id = r.charge_id"
                + " join appointment a on a.id = c.appointment_id"
                + " join practitioner p on p.id = a.practitioner_id"
                + " where p.clinic_id = ? and r.created_at >= ? and r.created_at < ?",
            clinicId(),
            start,
            end);
    var returned =
        jdbc.queryForList(
            "select f.created_at as at, f.amount_minor as amount, c.price_mode as mode,"
                + " a.service_name as service, p.id as practitioner_id, p.full_name as practitioner"
                + " from refund_record f join charge c on c.id = f.charge_id"
                + " join appointment a on a.id = c.appointment_id"
                + " join practitioner p on p.id = a.practitioner_id"
                + " where p.clinic_id = ? and f.created_at >= ? and f.created_at < ?",
            clinicId(),
            start,
            end);
    for (var sale : paid) add(rows, group, sale, zone, false);
    for (var refund : returned) add(rows, group, refund, zone, true);
    var list = new ArrayList<>(rows.values());
    list.sort(
        "day".equals(group)
            ? Comparator.comparing(Row::key)
            : Comparator.comparingLong(Row::net).reversed().thenComparing(Row::label));
    long payments = 0, amount = 0, refundsCount = 0, refunded = 0, open = 0;
    var result = new ArrayList<SalesReport.Line>();
    for (var row : list) {
      payments += row.payments;
      amount += row.amount;
      refundsCount += row.refunds;
      refunded += row.refunded;
      open += row.openPrice;
      result.add(row.line());
    }
    return new SalesReport(
        from,
        to,
        group,
        "RUB",
        result,
        new SalesReport.Totals(payments, amount, refundsCount, refunded, amount - refunded, open));
  }

  private static void add(
      Map<String, Row> rows,
      String group,
      Map<String, Object> sale,
      java.time.ZoneId zone,
      boolean refund) {
    var at = instant(sale.get("at"));
    var amount = ((Number) sale.get("amount")).longValue();
    String key;
    String label;
    switch (group) {
      case "service" -> {
        label = (String) sale.get("service");
        key = label;
      }
      case "practitioner" -> {
        key = String.valueOf(sale.get("practitioner_id"));
        label = (String) sale.get("practitioner");
      }
      default -> {
        key = at.atZone(zone).toLocalDate().toString();
        label = key;
      }
    }
    var row = rows.computeIfAbsent(key, k -> new Row(k, label));
    if (refund) {
      row.refunds++;
      row.refunded += amount;
    } else {
      row.payments++;
      row.amount += amount;
      if ("OPEN".equals(sale.get("mode"))) row.openPrice += amount;
    }
  }

  /** Момент из JDBC: H2 отдаёт OffsetDateTime, PostgreSQL — Timestamp или OffsetDateTime. */
  private static Instant instant(Object value) {
    return switch (value) {
      case Timestamp timestamp -> timestamp.toInstant();
      case java.time.OffsetDateTime offset -> offset.toInstant();
      case Instant instant -> instant;
      default -> throw new IllegalStateException("Unexpected time value: " + value.getClass());
    };
  }

  /** Накопитель строки отчёта. */
  private static final class Row {
    final String key;
    final String label;
    long payments;
    long amount;
    long refunds;
    long refunded;
    long openPrice;

    Row(String key, String label) {
      this.key = key;
      this.label = label;
    }

    String key() {
      return key;
    }

    String label() {
      return label;
    }

    long net() {
      return amount - refunded;
    }

    SalesReport.Line line() {
      return new SalesReport.Line(
          key, label, payments, amount, refunds, refunded, net(), openPrice);
    }
  }
}
