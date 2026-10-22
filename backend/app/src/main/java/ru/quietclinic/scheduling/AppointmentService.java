package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.forbidden;
import static ru.quietclinic.platform.PlatformConfig.hasRole;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.billing.ChargeService;
import ru.quietclinic.clinical.CareRelationshipService;
import ru.quietclinic.communications.OutboxService;

/** Записи на приём: расписание, история, создание, перенос, отмена и смена статуса. */
@Service
class AppointmentService {
  static final int MAX_RANGE_DAYS = 3 * 366;
  static final Set<String> RESCHEDULABLE = Set.of("BOOKED", "CONFIRMED");

  private final AppointmentRepository appointments;
  private final PractitionerRepository practitioners;
  private final PractitionerScheduleRepository workingHours;
  private final ClinicServiceRepository services;
  private final CareRelationshipService careRelationships;
  private final ChargeService charges;
  private final AuditService audit;
  private final OutboxService outbox;

  AppointmentService(
      AppointmentRepository appointments,
      PractitionerRepository practitioners,
      PractitionerScheduleRepository workingHours,
      ClinicServiceRepository services,
      CareRelationshipService careRelationships,
      ChargeService charges,
      AuditService audit,
      OutboxService outbox) {
    this.appointments = appointments;
    this.practitioners = practitioners;
    this.workingHours = workingHours;
    this.services = services;
    this.careRelationships = careRelationships;
    this.charges = charges;
    this.audit = audit;
    this.outbox = outbox;
  }

  /**
   * Либо один день ({@code date}), либо диапазон {@code from}–{@code to} включительно, например
   * вся история пациента. Фильтры по пациенту, врачу и статусу необязательны.
   */
  @Transactional(readOnly = true)
  List<AppointmentView> find(AppointmentQuery query) {
    requireRole("ADMINISTRATOR", "DOCTOR", "NURSE", "CASHIER", "MANAGER");
    LocalDate from;
    LocalDate to;
    if (query.date() != null) {
      from = query.date();
      to = query.date();
    } else if (query.from() != null && query.to() != null) {
      from = query.from();
      to = query.to();
      if (to.isBefore(from)) {
        throw conflict("INVALID_RANGE", "Конец периода должен быть не раньше начала");
      }
      if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
        throw conflict("RANGE_TOO_LONG", "Период не может быть длиннее трёх лет");
      }
    } else {
      throw conflict("INVALID_RANGE", "Укажите день (date) или период (from и to)");
    }
    var start = from.atStartOfDay().toInstant(ZoneOffset.UTC);
    var end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    var rows =
        query.patientId() == null
            ? appointments.findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                start, end)
            : appointments
                .findByPatientIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                    query.patientId(), start, end);
    return rows.stream()
        .filter(a -> query.patientId() == null || a.patientId.equals(query.patientId()))
        .filter(
            a -> query.practitionerId() == null || a.practitionerId.equals(query.practitionerId()))
        .filter(a -> query.status() == null || a.status.equals(query.status()))
        .map(AppointmentView::of)
        .toList();
  }

  @Transactional
  AppointmentView create(CreateAppointmentRequest request) {
    requireRole("ADMINISTRATOR", "DOCTOR");
    var service =
        services
            .findByName(request.serviceName())
            .orElseThrow(() -> conflict("UNKNOWN_SERVICE", "Такой услуги нет в справочнике"));
    checkSlot(request.practitionerId(), request.startsAt(), request.endsAt(), null);
    try {
      var appointment =
          appointments.saveAndFlush(
              new Appointment(
                  request.patientId(),
                  request.practitionerId(),
                  request.startsAt(),
                  request.endsAt(),
                  service.name,
                  service.priceMinor,
                  service.currency));
      careRelationships.ensureCareRelationship(appointment.practitionerId, appointment.patientId);
      audit.record(
          actorId(), "APPOINTMENT_CREATED", "APPOINTMENT", appointment.id, "ALLOWED", "booking");
      outbox.enqueue(
          "NOTIFICATION",
          "APPOINTMENT",
          appointment.id,
          "{\"template\":\"appointment-confirmed\",\"neutral\":true}");
      return AppointmentView.of(appointment);
    } catch (DataIntegrityViolationException ex) {
      // Гонка двух записей на один слот: её ловит exclusion constraint PostgreSQL.
      throw conflict("SLOT_ALREADY_BOOKED", "Интервал врача уже занят");
    }
  }

  /** Перенос без смены статуса. Уведомление пациенту не отправляется. */
  @Transactional
  AppointmentView reschedule(UUID id, RescheduleRequest request) {
    requireRole("ADMINISTRATOR");
    var appointment = find(id);
    if (!RESCHEDULABLE.contains(appointment.status)) {
      throw conflict(
          "INVALID_STATUS_TRANSITION",
          "Перенести можно только ожидающую или подтверждённую запись");
    }
    checkSlot(request.practitionerId(), request.startsAt(), request.endsAt(), appointment.id);
    var reason =
        RescheduleReason.code(
            appointment.startsAt,
            request.startsAt(),
            !appointment.practitionerId.equals(request.practitionerId()));
    appointment.startsAt = request.startsAt();
    appointment.endsAt = request.endsAt();
    appointment.practitionerId = request.practitionerId();
    try {
      appointments.saveAndFlush(appointment);
    } catch (DataIntegrityViolationException ex) {
      throw conflict("SLOT_ALREADY_BOOKED", "Интервал врача уже занят");
    }
    careRelationships.ensureCareRelationship(appointment.practitionerId, appointment.patientId);
    audit.record(
        actorId(), "APPOINTMENT_RESCHEDULED", "APPOINTMENT", appointment.id, "ALLOWED", reason);
    return AppointmentView.of(appointment);
  }

  @Transactional
  AppointmentView cancel(UUID id) {
    requireRole("ADMINISTRATOR");
    var appointment = find(id);
    if ("CANCELLED".equals(appointment.status)) {
      throw conflict("ALREADY_CANCELLED", "Запись уже отменена");
    }
    if (!AppointmentStatusPolicy.ACTIVE.contains(appointment.status)) {
      throw conflict(
          "INVALID_STATUS_TRANSITION", "Завершённую запись или неявку отменить нельзя");
    }
    appointment.status = "CANCELLED";
    audit.record(
        actorId(), "APPOINTMENT_CANCELLED", "APPOINTMENT", appointment.id, "ALLOWED", "cancel");
    outbox.enqueue(
        "NOTIFICATION",
        "APPOINTMENT",
        appointment.id,
        "{\"template\":\"appointment-cancelled\",\"neutral\":true}");
    return AppointmentView.of(appointment);
  }

  /** При переходе в COMPLETED модуль оплаты создаёт счёт на снимок цены записи. */
  @Transactional
  AppointmentView changeStatus(UUID id, String target) {
    requireRole("ADMINISTRATOR", "DOCTOR");
    var appointment = find(id);
    var permitted =
        (hasRole("ADMINISTRATOR") && AppointmentStatusPolicy.administratorMay(target))
            || (hasRole("DOCTOR")
                && AppointmentStatusPolicy.doctorMay(target)
                && actorId().equals(appointment.practitionerId));
    if (!permitted) {
      audit.record(
          actorId(), "APPOINTMENT_STATUS_CHANGE", "APPOINTMENT", id, "DENIED", "to " + target);
      throw forbidden("Недостаточно прав для смены статуса");
    }
    if (!AppointmentStatusPolicy.allowed(appointment.status, target)) {
      throw conflict(
          "INVALID_STATUS_TRANSITION",
          "Нельзя перевести запись из статуса " + appointment.status + " в " + target);
    }
    var from = appointment.status;
    appointment.status = target;
    appointments.saveAndFlush(appointment);
    audit.record(
        actorId(),
        "APPOINTMENT_STATUS_CHANGED",
        "APPOINTMENT",
        appointment.id,
        "ALLOWED",
        from + "->" + target);
    if ("COMPLETED".equals(target) && appointment.amountMinor != null) {
      charges.chargeForCompletedVisit(
          appointment.id, appointment.amountMinor, appointment.currency);
    }
    return AppointmentView.of(appointment);
  }

  /** Длина интервала, врач, рабочие часы и пересечение — общие для создания и переноса. */
  private void checkSlot(UUID practitionerId, Instant startsAt, Instant endsAt, UUID excludedId) {
    if (!endsAt.isAfter(startsAt)) {
      throw conflict("INVALID_INTERVAL", "Конец записи должен быть позже начала");
    }
    if (!practitioners.existsById(practitionerId)) {
      throw notFound("Врач не найден");
    }
    if (!WorkingHoursPolicy.fits(
        workingHours.findByPractitionerIdOrderByDayOfWeek(practitionerId), startsAt, endsAt)) {
      throw conflict("OUTSIDE_WORKING_HOURS", "Время вне рабочих часов врача");
    }
    var overlaps =
        excludedId == null
            ? appointments.countOverlaps(practitionerId, startsAt, endsAt)
            : appointments.countOverlapsExcluding(practitionerId, startsAt, endsAt, excludedId);
    if (overlaps > 0) {
      throw conflict("SLOT_ALREADY_BOOKED", "Интервал врача уже занят");
    }
  }

  private Appointment find(UUID id) {
    return appointments.findById(id).orElseThrow(() -> notFound("Запись не найдена"));
  }
}
