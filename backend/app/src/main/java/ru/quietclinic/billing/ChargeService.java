package ru.quietclinic.billing;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;

/** Счета за приёмы. Один счёт на запись — уникальность по appointment_id. */
@Service
public class ChargeService {
  private final ChargeRepository charges;
  private final PaymentRepository payments;
  private final AuditService audit;
  private final JdbcTemplate jdbc;

  ChargeService(
      ChargeRepository charges, PaymentRepository payments, AuditService audit, JdbcTemplate jdbc) {
    this.charges = charges;
    this.payments = payments;
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
   * Счёт как следствие завершения приёма. Права не проверяет: вызывается модулем расписания, а не
   * по HTTP. Повторный вызов возвращает уже созданный счёт.
   */
  @Transactional
  public UUID chargeForCompletedVisit(UUID appointmentId, long amountMinor, String currency) {
    var existing = charges.findByAppointmentId(appointmentId);
    if (existing.isPresent()) return existing.get().id;
    var charge = charges.saveAndFlush(new Charge(appointmentId, amountMinor, currency));
    audit.record(
        actorId(), "CHARGE_CREATED", "CHARGE", charge.id, "ALLOWED", "appointment-completed");
    return charge.id;
  }

  /** Ручное выставление счёта по HTTP: администратор или кассир, если счёта ещё нет. */
  @Transactional
  ChargeView create(ChargeRequest request) {
    requireRole("CASHIER", "ADMINISTRATOR");
    if (!clinicAppointments().contains(request.appointmentId())) {
      throw notFound("APPOINTMENT_NOT_FOUND", "Appointment not found");
    }
    if (charges.findByAppointmentId(request.appointmentId()).isPresent()) {
      throw conflict("CHARGE_ALREADY_EXISTS", "A charge for this visit already exists");
    }
    var charge =
        charges.saveAndFlush(
            new Charge(request.appointmentId(), request.amountMinor(), request.currency()));
    audit.record(actorId(), "CHARGE_CREATED", "CHARGE", charge.id, "ALLOWED", "billing");
    return ChargeView.of(charge, null);
  }

  @Transactional(readOnly = true)
  List<ChargeView> list() {
    requireRole("CASHIER", "ADMINISTRATOR");
    var own = clinicAppointments();
    return charges.findAllByOrderByCreatedAtDesc().stream()
        .filter(c -> own.contains(c.appointmentId))
        .map(
            c ->
                ChargeView.of(
                    c, payments.findFirstByChargeIdOrderByCreatedAtDesc(c.id).orElse(null)))
        .toList();
  }
}
