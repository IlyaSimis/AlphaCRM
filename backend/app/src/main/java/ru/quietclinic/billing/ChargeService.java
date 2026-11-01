package ru.quietclinic.billing;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.Permission;

/** Счета за приёмы. Один счёт на запись — уникальность по appointment_id. */
@Service
public class ChargeService {
  private final ChargeRepository charges;
  private final PaymentRepository payments;
  private final RefundRepository refunds;
  private final AuditService audit;
  private final JdbcTemplate jdbc;

  ChargeService(
      ChargeRepository charges,
      PaymentRepository payments,
      RefundRepository refunds,
      AuditService audit,
      JdbcTemplate jdbc) {
    this.charges = charges;
    this.payments = payments;
    this.refunds = refunds;
    this.audit = audit;
    this.jdbc = jdbc;
  }

  /** Записи своей клиники (через врача записи) — запросом к таблицам, без кода расписания. */
  Set<UUID> clinicAppointments() {
    return new HashSet<>(
        jdbc.queryForList(
            "select a.id from appointment a join practitioner p on p.id = a.practitioner_id"
                + " where p.clinic_id = ?",
            UUID.class,
            clinicId()));
  }

  boolean inClinic(Charge charge) {
    return clinicAppointments().contains(charge.appointmentId);
  }

  /**
   * Счёт как следствие завершения приёма. Права не проверяет: вызывается потребителем события
   * {@code AppointmentCompleted}, а не по HTTP. Повторный вызов возвращает уже созданный счёт.
   */
  @Transactional
  public UUID chargeForCompletedVisit(UUID appointmentId, long amountMinor, String currency) {
    Optional<Charge> existing = charges.findByAppointmentId(appointmentId);
    if (existing.isPresent()) return existing.get().id;
    Charge charge = charges.saveAndFlush(new Charge(appointmentId, amountMinor, currency));
    audit.record(
        actorId(), "CHARGE_CREATED", "CHARGE", charge.id, "ALLOWED", "appointment-completed");
    return charge.id;
  }

  /** Счёт по свободной цене: ждёт суммы в пределах услуги. Повтор возвращает тот же счёт. */
  @Transactional
  public UUID awaitAmountForCompletedVisit(
      UUID appointmentId, String currency, Long minMinor, Long maxMinor, Long estimateMinor) {
    Optional<Charge> existing = charges.findByAppointmentId(appointmentId);
    if (existing.isPresent()) return existing.get().id;
    Charge charge =
        charges.saveAndFlush(
            Charge.awaitingAmount(appointmentId, currency, minMinor, maxMinor, estimateMinor));
    audit.record(
        actorId(), "CHARGE_CREATED", "CHARGE", charge.id, "ALLOWED", "appointment-completed:open");
    return charge.id;
  }

  /** Сумма свободной цены: администратор или кассир, в пределах услуги. */
  @Transactional
  ChargeView setAmount(UUID chargeId, ChargeAmountRequest request) {
    requirePermission(Permission.INVOICE_SET_AMOUNT);
    Charge charge =
        charges
            .findById(chargeId)
            .filter(this::inClinic)
            .orElseThrow(() -> notFound("CHARGE_NOT_FOUND", "Charge not found"));
    charge.setAmount(request.amountMinor());
    charges.saveAndFlush(charge);
    audit.record(actorId(), "CHARGE_AMOUNT_SET", "CHARGE", charge.id, "ALLOWED", "open-price");
    return ChargeView.of(
        charge, payments.findFirstByChargeIdOrderByCreatedAtDesc(charge.id).orElse(null));
  }

  /** Ручное выставление счёта по HTTP: администратор или кассир, если счёта ещё нет. */
  @Transactional
  ChargeView create(ChargeRequest request) {
    requirePermission(Permission.INVOICE_WRITE);
    if (!clinicAppointments().contains(request.appointmentId())) {
      throw notFound("APPOINTMENT_NOT_FOUND", "Appointment not found");
    }
    if (charges.findByAppointmentId(request.appointmentId()).isPresent()) {
      throw conflict("CHARGE_ALREADY_EXISTS", "A charge for this visit already exists");
    }
    Charge charge =
        charges.saveAndFlush(
            new Charge(request.appointmentId(), request.amountMinor(), request.currency()));
    audit.record(actorId(), "CHARGE_CREATED", "CHARGE", charge.id, "ALLOWED", "billing");
    return ChargeView.of(charge, null);
  }

  @Transactional(readOnly = true)
  List<ChargeView> list() {
    requirePermission(Permission.INVOICE_READ);
    Set<UUID> own = clinicAppointments();
    return charges.findAllByOrderByCreatedAtDesc().stream()
        .filter(c -> own.contains(c.appointmentId))
        .map(this::view)
        .toList();
  }

  ChargeView view(Charge charge) {
    return ChargeView.of(
        charge,
        payments.findFirstByChargeIdOrderByCreatedAtDesc(charge.id).orElse(null),
        refunds.findByChargeId(charge.id).orElse(null));
  }
}
