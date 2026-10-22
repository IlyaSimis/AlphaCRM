package ru.quietclinic.billing;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;

/** Счета за приёмы. Один счёт на запись — уникальность по appointment_id. */
@Service
public class ChargeService {
  private final ChargeRepository charges;
  private final PaymentRepository payments;
  private final AuditService audit;

  ChargeService(ChargeRepository charges, PaymentRepository payments, AuditService audit) {
    this.charges = charges;
    this.payments = payments;
    this.audit = audit;
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
    if (charges.findByAppointmentId(request.appointmentId()).isPresent()) {
      throw conflict("CHARGE_ALREADY_EXISTS", "Счёт по этому приёму уже выставлен");
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
    return charges.findAllByOrderByCreatedAtDesc().stream()
        .map(
            c ->
                ChargeView.of(
                    c, payments.findFirstByChargeIdOrderByCreatedAtDesc(c.id).orElse(null)))
        .toList();
  }
}
