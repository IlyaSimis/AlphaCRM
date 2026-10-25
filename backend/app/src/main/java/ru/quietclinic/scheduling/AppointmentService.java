package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
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
  private final PractitionerScheduleRepository workingHours;
  private final ClinicServiceRepository services;
  private final CareRelationshipService careRelationships;
  private final ChargeService charges;
  private final AuditService audit;
  private final OutboxService outbox;
  private final ClinicScope clinic;

  AppointmentService(
      AppointmentRepository appointments,
      PractitionerScheduleRepository workingHours,
      ClinicServiceRepository services,
      CareRelationshipService careRelationships,
      ChargeService charges,
      AuditService audit,
      OutboxService outbox,
      ClinicScope clinic) {
    this.appointments = appointments;
    this.workingHours = workingHours;
    this.services = services;
    this.careRelationships = careRelationships;
    this.charges = charges;
    this.audit = audit;
    this.outbox = outbox;
    this.clinic = clinic;
  }

  /**
   * Либо один день ({@code date}), либо диапазон {@code from}–{@code to} включительно, например вся
   * история пациента. Фильтры по пациенту, врачу и статусу необязательны.
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
        throw conflict("INVALID_RANGE", "Period end must not be before its start");
      }
      if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
        throw conflict("RANGE_TOO_LONG", "Period must not exceed three years");
      }
    } else {
      throw conflict("INVALID_RANGE", "Pass a day (date) or a period (from and to)");
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
    // Только записи к врачам своей клиники.
    var ownPractitioners = clinic.practitionerIds();
    return rows.stream()
        .filter(a -> ownPractitioners.contains(a.practitionerId))
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
            .findByClinicIdAndName(clinicId(), request.serviceName())
            .orElseThrow(() -> conflict("UNKNOWN_SERVICE", "Service is not in the catalog"));
    // Выключенная услуга не предлагается в новой записи; старые записи её снимок сохраняют.
    if (!service.active) throw conflict("SERVICE_INACTIVE", "The service is switched off");
    if (!clinic.hasPatient(request.patientId())) {
      throw notFound("PATIENT_NOT_FOUND", "Patient not found");
    }
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
      // Подтверждение и, если запись на будущий день, напоминание за сутки. Текст и адрес
      // собирает доставщик outbox из записи.
      outbox.appointmentBooked(appointment.id, appointment.startsAt);
      return AppointmentView.of(appointment);
    } catch (DataIntegrityViolationException ex) {
      // Гонка двух записей на один слот: её ловит exclusion constraint PostgreSQL.
      throw conflict("SLOT_ALREADY_BOOKED", "The practitioner's slot is already booked");
    }
  }

  /**
   * Перенос без смены статуса. В outbox перенос ничего не кладёт: доставщик сам отменит
   * неотправленное подтверждение на старое время и поставит новое.
   */
  @Transactional
  AppointmentView reschedule(UUID id, RescheduleRequest request) {
    requireRole("ADMINISTRATOR");
    var appointment = find(id);
    if (!RESCHEDULABLE.contains(appointment.status)) {
      throw conflict(
          "INVALID_STATUS_TRANSITION", "Only booked or confirmed appointments can be rescheduled");
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
      throw conflict("SLOT_ALREADY_BOOKED", "The practitioner's slot is already booked");
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
      throw conflict("ALREADY_CANCELLED", "Appointment is already cancelled");
    }
    if (!AppointmentStatusPolicy.ACTIVE.contains(appointment.status)) {
      throw conflict(
          "INVALID_STATUS_TRANSITION", "Completed or missed appointments cannot be cancelled");
    }
    appointment.status = "CANCELLED";
    audit.record(
        actorId(), "APPOINTMENT_CANCELLED", "APPOINTMENT", appointment.id, "ALLOWED", "cancel");
    outbox.appointmentCancelled(appointment.id, appointment.startsAt);
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
      throw forbidden("STATUS_CHANGE_FORBIDDEN", "Role cannot change this status");
    }
    if (!AppointmentStatusPolicy.allowed(appointment.status, target)) {
      throw conflict(
          "INVALID_STATUS_TRANSITION",
          "Cannot move appointment from " + appointment.status + " to " + target);
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

  /**
   * Старт приёма врачом: запись до приёма переводится в IN_PROGRESS. Уже идущий приём не меняется;
   * другие записи того же врача остаются как есть.
   */
  @Transactional
  void markInProgress(UUID id) {
    var appointment = find(id);
    if ("IN_PROGRESS".equals(appointment.status)) return;
    if (!AppointmentStatusPolicy.allowed(appointment.status, "IN_PROGRESS")) {
      throw conflict(
          "INVALID_STATUS_TRANSITION",
          "Cannot start a visit for an appointment in status " + appointment.status);
    }
    var from = appointment.status;
    appointment.status = "IN_PROGRESS";
    appointments.saveAndFlush(appointment);
    audit.record(
        actorId(),
        "APPOINTMENT_STATUS_CHANGED",
        "APPOINTMENT",
        appointment.id,
        "ALLOWED",
        from + "->IN_PROGRESS");
  }

  /** Длина интервала, врач, рабочие часы и пересечение — общие для создания и переноса. */
  private void checkSlot(UUID practitionerId, Instant startsAt, Instant endsAt, UUID excludedId) {
    if (!endsAt.isAfter(startsAt)) {
      throw conflict("INVALID_INTERVAL", "Appointment end must be after its start");
    }
    if (!clinic.hasPractitioner(practitionerId)) {
      throw notFound("PRACTITIONER_NOT_FOUND", "Practitioner not found");
    }
    if (!WorkingHoursPolicy.fits(
        workingHours.findByPractitionerIdOrderByDayOfWeek(practitionerId), startsAt, endsAt)) {
      throw conflict("OUTSIDE_WORKING_HOURS", "Outside the practitioner's working hours");
    }
    var overlaps =
        excludedId == null
            ? appointments.countOverlaps(practitionerId, startsAt, endsAt)
            : appointments.countOverlapsExcluding(practitionerId, startsAt, endsAt, excludedId);
    if (overlaps > 0) {
      throw conflict("SLOT_ALREADY_BOOKED", "The practitioner's slot is already booked");
    }
  }

  private Appointment find(UUID id) {
    return appointments
        .findById(id)
        .filter(clinic::hasAppointment)
        .orElseThrow(() -> notFound("APPOINTMENT_NOT_FOUND", "Appointment not found"));
  }
}
