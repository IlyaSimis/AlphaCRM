package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.can;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.forbidden;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.clinical.CareRelationshipService;
import ru.quietclinic.communications.OutboxService;
import ru.quietclinic.platform.ClinicTime;
import ru.quietclinic.platform.DomainEvents;
import ru.quietclinic.platform.Permission;

/**
 * Записи на приём: расписание, история, создание, перенос, отмена и смена статуса. Записывают,
 * переносят и отменяют сотрудники с {@code schedule.appointment.write}; врач — только свои записи
 * ({@code write-own}). Завершение приёма публикует событие {@code AppointmentCompleted}: счёт
 * выставляет модуль оплаты, расход по нормам проводит склад.
 */
@Service
class AppointmentService {
  static final int MAX_RANGE_DAYS = 3 * 366;
  static final Set<String> RESCHEDULABLE = Set.of("BOOKED", "CONFIRMED");

  /** Основание в аудите для действий пациента через Telegram-бота. */
  static final String ONLINE = "telegram-bot";

  private final AppointmentRepository appointments;
  private final PractitionerScheduleRepository workingHours;
  private final ClinicServiceRepository services;
  private final CareRelationshipService careRelationships;
  private final ServiceNormRepository norms;
  private final DomainEvents events;
  private final AuditService audit;
  private final OutboxService outbox;
  private final ClinicScope clinic;
  private final ClinicTime time;

  AppointmentService(
      AppointmentRepository appointments,
      PractitionerScheduleRepository workingHours,
      ClinicServiceRepository services,
      CareRelationshipService careRelationships,
      ServiceNormRepository norms,
      DomainEvents events,
      AuditService audit,
      OutboxService outbox,
      ClinicScope clinic,
      ClinicTime time) {
    this.appointments = appointments;
    this.workingHours = workingHours;
    this.services = services;
    this.careRelationships = careRelationships;
    this.norms = norms;
    this.events = events;
    this.audit = audit;
    this.outbox = outbox;
    this.clinic = clinic;
    this.time = time;
  }

  /**
   * Либо один день ({@code date}), либо диапазон {@code from}–{@code to} включительно, например вся
   * история пациента. Фильтры по пациенту, врачу и статусу необязательны.
   */
  @Transactional(readOnly = true)
  List<AppointmentView> find(AppointmentQuery query) {
    requirePermission(Permission.SCHEDULE_READ);
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
    // День — в часовом поясе клиники.
    Instant start = time.startOf(from);
    Instant end = time.startOf(to.plusDays(1));
    List<Appointment> rows =
        query.patientId() == null
            ? appointments.findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                start, end)
            : appointments
                .findByPatientIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
                    query.patientId(), start, end);
    // Только записи к врачам своей клиники.
    Set<UUID> ownPractitioners = clinic.practitionerIds();
    return rows.stream()
        .filter(a -> ownPractitioners.contains(a.practitionerId))
        .filter(a -> query.patientId() == null || a.patientId.equals(query.patientId()))
        .filter(
            a -> query.practitionerId() == null || a.practitionerId.equals(query.practitionerId()))
        .filter(a -> query.status() == null || a.status.equals(query.status()))
        .map(AppointmentService::visible)
        .toList();
  }

  /** Маска для ролей без доступа к пациентам: без идентификатора пациента. */
  static AppointmentView visible(Appointment appointment) {
    AppointmentView view = AppointmentView.of(appointment);
    return can(Permission.PATIENT_READ, Permission.PATIENT_READ_CARE) ? view : view.masked();
  }

  /** Врач с {@code write-own} меняет только записи к себе. */
  private void requireWrite(UUID practitionerId) {
    if (can(Permission.APPOINTMENT_WRITE)) return;
    requirePermission(Permission.APPOINTMENT_WRITE_OWN);
    if (!actorId().equals(practitionerId)) {
      throw forbidden("NOT_OWN_APPOINTMENT", "Only your own appointments can be changed");
    }
  }

  @Transactional
  AppointmentView create(CreateAppointmentRequest request) {
    requirePermission(Permission.APPOINTMENT_WRITE, Permission.APPOINTMENT_WRITE_OWN);
    requireWrite(request.practitionerId());
    ClinicService service = activeService(request.serviceName());
    Long estimate = request.estimateMinor();
    if (estimate != null && (!service.open() || !service.withinLimits(estimate))) {
      throw conflict("ESTIMATE_OUT_OF_RANGE", "The estimate must be within the open price limits");
    }
    if (!clinic.hasPatient(request.patientId())) {
      throw notFound("PATIENT_NOT_FOUND", "Patient not found");
    }
    return book(
        request.patientId(),
        request.practitionerId(),
        request.startsAt(),
        request.endsAt(),
        service,
        estimate,
        request.metricaClientId(),
        "booking",
        false);
  }

  /**
   * Запись, которую пациент сделал сам в Telegram-боте. Проверки слота те же, что у регистратуры;
   * конец приёма — по длительности услуги. Права проверяет {@link OnlineBooking}.
   */
  @Transactional
  AppointmentView createOnline(
      UUID patientId, UUID practitionerId, Instant startsAt, String serviceName) {
    ClinicService service = activeService(serviceName);
    if (!clinic.hasPatient(patientId)) throw notFound("PATIENT_NOT_FOUND", "Patient not found");
    Instant endsAt = startsAt.plus(Duration.ofMinutes(service.durationMinutes));
    return book(patientId, practitionerId, startsAt, endsAt, service, null, null, ONLINE, true);
  }

  /** Пациент подтвердил запись кнопкой в Telegram. Уже подтверждённая остаётся как есть. */
  @Transactional
  AppointmentView confirmByPatient(UUID id) {
    Appointment appointment = find(id);
    if ("CONFIRMED".equals(appointment.status)) return AppointmentView.of(appointment);
    if (!"BOOKED".equals(appointment.status)) {
      throw conflict(
          "INVALID_STATUS_TRANSITION",
          "Cannot confirm an appointment in status " + appointment.status);
    }
    appointment.status = "CONFIRMED";
    appointments.saveAndFlush(appointment);
    audit.record(
        actorId(),
        "APPOINTMENT_STATUS_CHANGED",
        "APPOINTMENT",
        appointment.id,
        "ALLOWED",
        "BOOKED->CONFIRMED");
    return AppointmentView.of(appointment);
  }

  /**
   * Пациент сам отменил запись в Telegram. Сообщение об отмене не ставится: пациент только что
   * увидел ответ бота; неотправленное напоминание доставщик закроет сам по статусу записи.
   */
  @Transactional
  AppointmentView cancelByPatient(UUID id) {
    Appointment appointment = find(id);
    if ("CANCELLED".equals(appointment.status)) {
      throw conflict("ALREADY_CANCELLED", "Appointment is already cancelled");
    }
    if (!RESCHEDULABLE.contains(appointment.status)) {
      throw conflict(
          "INVALID_STATUS_TRANSITION", "Only booked or confirmed appointments can be cancelled");
    }
    appointment.status = "CANCELLED";
    appointments.saveAndFlush(appointment);
    audit.record(
        actorId(), "APPOINTMENT_CANCELLED", "APPOINTMENT", appointment.id, "ALLOWED", ONLINE);
    return AppointmentView.of(appointment);
  }

  @Transactional(readOnly = true)
  Optional<AppointmentView> findView(UUID id) {
    return appointments.findById(id).filter(clinic::hasAppointment).map(AppointmentView::of);
  }

  private ClinicService activeService(String serviceName) {
    ClinicService service =
        services
            .findByClinicIdAndName(clinicId(), serviceName)
            .orElseThrow(() -> conflict("UNKNOWN_SERVICE", "Service is not in the catalog"));
    // Выключенная услуга не предлагается в новой записи; старые записи её снимок сохраняют.
    if (!service.active) throw conflict("SERVICE_INACTIVE", "The service is switched off");
    return service;
  }

  /**
   * Общая часть создания записи. {@code online} — запись из бота: подтверждение пациент уже видел в
   * боте, поэтому в outbox ставится только напоминание за сутки.
   */
  private AppointmentView book(
      UUID patientId,
      UUID practitionerId,
      Instant startsAt,
      Instant endsAt,
      ClinicService service,
      Long estimateMinor,
      String metricaClientId,
      String reason,
      boolean online) {
    checkSlot(practitionerId, startsAt, endsAt, null);
    try {
      Appointment draft =
          new Appointment(
              patientId,
              practitionerId,
              startsAt,
              endsAt,
              service.name,
              service.priceMinor,
              service.currency);
      // Снимок услуги: свободная цена — без суммы, с необязательной оценкой.
      draft.serviceId = service.id;
      draft.priceMode = service.priceMode;
      draft.estimateMinor = service.open() ? estimateMinor : null;
      draft.metricaClientId = metricaClientId;
      Appointment appointment = appointments.saveAndFlush(draft);
      careRelationships.ensureCareRelationship(appointment.practitionerId, appointment.patientId);
      audit.record(
          actorId(), "APPOINTMENT_CREATED", "APPOINTMENT", appointment.id, "ALLOWED", reason);
      // Подтверждение и, если запись на будущий день, напоминание за сутки. Текст и адрес
      // собирает доставщик outbox из записи.
      if (online) outbox.appointmentBookedOnline(appointment.id, appointment.startsAt);
      else outbox.appointmentBooked(appointment.id, appointment.startsAt);
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
    requirePermission(Permission.APPOINTMENT_WRITE, Permission.APPOINTMENT_WRITE_OWN);
    Appointment appointment = find(id);
    requireWrite(appointment.practitionerId);
    requireWrite(request.practitionerId());
    if (!RESCHEDULABLE.contains(appointment.status)) {
      throw conflict(
          "INVALID_STATUS_TRANSITION", "Only booked or confirmed appointments can be rescheduled");
    }
    checkSlot(request.practitionerId(), request.startsAt(), request.endsAt(), appointment.id);
    String reason =
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
    requirePermission(Permission.APPOINTMENT_WRITE, Permission.APPOINTMENT_WRITE_OWN);
    Appointment appointment = find(id);
    requireWrite(appointment.practitionerId);
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

  /**
   * При переходе в COMPLETED публикуется {@code AppointmentCompleted}: модуль оплаты выставляет
   * счёт на снимок цены записи (или счёт «ждёт суммы» при свободной цене), склад проводит расход.
   */
  @Transactional
  AppointmentView changeStatus(UUID id, String target) {
    requirePermission(Permission.APPOINTMENT_WRITE, Permission.APPOINTMENT_WRITE_OWN);
    Appointment appointment = find(id);
    boolean permitted =
        (can(Permission.APPOINTMENT_WRITE) && AppointmentStatusPolicy.administratorMay(target))
            || (can(Permission.APPOINTMENT_WRITE_OWN)
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
    String from = appointment.status;
    appointment.status = target;
    appointments.saveAndFlush(appointment);
    audit.record(
        actorId(),
        "APPOINTMENT_STATUS_CHANGED",
        "APPOINTMENT",
        appointment.id,
        "ALLOWED",
        from + "->" + target);
    if ("COMPLETED".equals(target)) publishCompleted(appointment);
    return AppointmentView.of(appointment);
  }

  /**
   * Конверт {@code AppointmentCompleted}: только идентификаторы, снимок цены и нормы расхода
   * услуги. Пределы свободной цены — из справочника на момент завершения.
   */
  private void publishCompleted(Appointment appointment) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("appointmentId", appointment.id.toString());
    payload.put("patientId", appointment.patientId.toString());
    payload.put("practitionerId", appointment.practitionerId.toString());
    payload.put(
        "serviceId", appointment.serviceId == null ? null : appointment.serviceId.toString());
    payload.put("priceMode", appointment.priceMode);
    payload.put("amountMinor", appointment.amountMinor);
    payload.put("estimateMinor", appointment.estimateMinor);
    payload.put("currency", appointment.currency);
    List<Map<String, Object>> consumption = new ArrayList<>();
    if (appointment.serviceId != null) {
      services
          .findByIdAndClinicId(appointment.serviceId, clinicId())
          .ifPresent(
              service -> {
                payload.put("minMinor", service.minMinor);
                payload.put("maxMinor", service.maxMinor);
              });
      for (ServiceNorm norm : norms.findByServiceId(appointment.serviceId)) {
        consumption.add(
            Map.of("itemId", norm.itemId.toString(), "quantity", norm.quantity.toPlainString()));
      }
    }
    payload.put("consumption", consumption);
    events.publish("AppointmentCompleted", payload);
  }

  /**
   * Старт приёма врачом: запись до приёма переводится в IN_PROGRESS. Уже идущий приём не меняется;
   * другие записи того же врача остаются как есть.
   */
  @Transactional
  void markInProgress(UUID id) {
    Appointment appointment = find(id);
    if ("IN_PROGRESS".equals(appointment.status)) return;
    if (!AppointmentStatusPolicy.allowed(appointment.status, "IN_PROGRESS")) {
      throw conflict(
          "INVALID_STATUS_TRANSITION",
          "Cannot start a visit for an appointment in status " + appointment.status);
    }
    String from = appointment.status;
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
    // Запись в прошлое и перенос в прошлое не принимаются: время приёма уже прошло.
    if (startsAt.isBefore(time.now())) {
      throw conflict("APPOINTMENT_IN_PAST", "The appointment time has already passed");
    }
    if (!WorkingHoursPolicy.fits(
        workingHours.findByPractitionerIdOrderByDayOfWeek(practitionerId),
        startsAt,
        endsAt,
        time.zone())) {
      throw conflict("OUTSIDE_WORKING_HOURS", "Outside the practitioner's working hours");
    }
    long overlaps =
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
