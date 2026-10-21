package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.forbidden;
import static ru.quietclinic.platform.PlatformConfig.hasRole;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.communications.OutboxService;

@Entity
@Table(name = "practitioner")
class Practitioner {
  @Id UUID id;

  @Column(nullable = false)
  String fullName;

  @Column(nullable = false)
  String specialty;

  protected Practitioner() {}
}

@Entity
@Table(name = "practitioner_schedule")
class PractitionerSchedule {
  @Id UUID id;

  @Column(nullable = false)
  UUID practitionerId;

  @Column(nullable = false)
  short dayOfWeek;

  @Column(nullable = false)
  java.time.LocalTime startsAt;

  @Column(nullable = false)
  java.time.LocalTime endsAt;

  protected PractitionerSchedule() {}
}

@Entity
@Table(name = "appointment")
class Appointment {
  @Id UUID id;

  @Column(nullable = false)
  UUID patientId;

  @Column(nullable = false)
  UUID practitionerId;

  @Column(nullable = false)
  Instant startsAt;

  @Column(nullable = false)
  Instant endsAt;

  @Column(nullable = false)
  String serviceName;

  @Column(nullable = false)
  String status;

  @Column(nullable = false)
  Instant createdAt;

  @Version long version;

  protected Appointment() {}

  Appointment(
      UUID patientId, UUID practitionerId, Instant startsAt, Instant endsAt, String serviceName) {
    id = UUID.randomUUID();
    this.patientId = patientId;
    this.practitionerId = practitionerId;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.serviceName = serviceName;
    status = "BOOKED";
    createdAt = Instant.now();
  }
}

interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
  @Query(
      """
      select count(a) from Appointment a
      where a.practitionerId = :practitionerId
        and a.status in ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS','COMPLETED')
        and a.startsAt < :endsAt and a.endsAt > :startsAt
      """)
  long countOverlaps(
      @Param("practitionerId") UUID practitionerId,
      @Param("startsAt") Instant startsAt,
      @Param("endsAt") Instant endsAt);

  List<Appointment> findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
      Instant from, Instant to);
}

interface PractitionerRepository extends JpaRepository<Practitioner, UUID> {}

interface PractitionerScheduleRepository extends JpaRepository<PractitionerSchedule, UUID> {
  List<PractitionerSchedule> findByPractitionerIdOrderByDayOfWeek(UUID practitionerId);
}

record CreateAppointmentRequest(
    @NotNull UUID patientId,
    @NotNull UUID practitionerId,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @NotNull String serviceName) {}

record ChangeStatusRequest(@NotBlank String status) {}

record WorkingHoursView(int dayOfWeek, java.time.LocalTime startsAt, java.time.LocalTime endsAt) {}

record PractitionerView(
    UUID id, String fullName, String specialty, List<WorkingHoursView> workingHours) {}

/** Допустимые переходы статуса записи и роли, которым они разрешены. */
final class AppointmentStatusPolicy {
  static final Set<String> ACTIVE = Set.of("BOOKED", "CONFIRMED", "ARRIVED", "IN_PROGRESS");

  private static final Map<String, Set<String>> TRANSITIONS =
      Map.of(
          "BOOKED", Set.of("CONFIRMED", "ARRIVED", "NO_SHOW"),
          "CONFIRMED", Set.of("ARRIVED", "NO_SHOW"),
          "ARRIVED", Set.of("IN_PROGRESS", "COMPLETED"),
          "IN_PROGRESS", Set.of("COMPLETED"));

  private static final Set<String> ADMINISTRATOR_TARGETS =
      Set.of("CONFIRMED", "ARRIVED", "NO_SHOW");
  private static final Set<String> DOCTOR_TARGETS = Set.of("IN_PROGRESS", "COMPLETED");

  private AppointmentStatusPolicy() {}

  static boolean allowed(String from, String to) {
    return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
  }

  static boolean administratorMay(String to) {
    return ADMINISTRATOR_TARGETS.contains(to);
  }

  static boolean doctorMay(String to) {
    return DOCTOR_TARGETS.contains(to);
  }
}

record AppointmentView(
    UUID id,
    UUID patientId,
    UUID practitionerId,
    Instant startsAt,
    Instant endsAt,
    String serviceName,
    String status,
    long version) {}

@RestController
@RequestMapping("/api/v1")
public class SchedulingController {
  private final AppointmentRepository appointments;
  private final PractitionerRepository practitioners;
  private final PractitionerScheduleRepository workingHours;
  private final AuditService audit;
  private final OutboxService outbox;

  SchedulingController(
      AppointmentRepository appointments,
      PractitionerRepository practitioners,
      PractitionerScheduleRepository workingHours,
      AuditService audit,
      OutboxService outbox) {
    this.appointments = appointments;
    this.practitioners = practitioners;
    this.workingHours = workingHours;
    this.audit = audit;
    this.outbox = outbox;
  }

  @GetMapping("/practitioners")
  @Transactional(readOnly = true)
  List<PractitionerView> practitioners() {
    requireRole("ADMINISTRATOR", "DOCTOR", "NURSE", "CASHIER", "MANAGER");
    return practitioners.findAll().stream()
        .sorted(Comparator.comparing((Practitioner p) -> p.fullName))
        .map(
            p ->
                new PractitionerView(
                    p.id,
                    p.fullName,
                    p.specialty,
                    workingHours.findByPractitionerIdOrderByDayOfWeek(p.id).stream()
                        .map(h -> new WorkingHoursView(h.dayOfWeek, h.startsAt, h.endsAt))
                        .toList()))
        .toList();
  }

  @GetMapping("/appointments")
  @Transactional(readOnly = true)
  List<AppointmentView> schedule(
      @RequestParam LocalDate date,
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) UUID practitionerId) {
    requireRole("ADMINISTRATOR", "DOCTOR", "NURSE", "CASHIER", "MANAGER");
    var from = date.atStartOfDay().toInstant(ZoneOffset.UTC);
    return appointments
        .findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
            from, date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))
        .stream()
        .filter(appointment -> patientId == null || appointment.patientId.equals(patientId))
        .filter(
            appointment ->
                practitionerId == null || appointment.practitionerId.equals(practitionerId))
        .map(this::view)
        .toList();
  }

  @PostMapping("/appointments/{id}/cancel")
  @Transactional
  AppointmentView cancel(@PathVariable UUID id) {
    requireRole("ADMINISTRATOR");
    var appointment = appointments.findById(id).orElseThrow(() -> notFound("Запись не найдена"));
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
    return view(appointment);
  }

  @PostMapping("/appointments/{id}/status")
  @Transactional
  AppointmentView changeStatus(
      @PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
    requireRole("ADMINISTRATOR", "DOCTOR");
    var target = request.status();
    var appointment = appointments.findById(id).orElseThrow(() -> notFound("Запись не найдена"));
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
    return view(appointment);
  }

  @PostMapping("/appointments")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  AppointmentView create(@Valid @RequestBody CreateAppointmentRequest request) {
    requireRole("ADMINISTRATOR", "DOCTOR");
    if (!request.endsAt().isAfter(request.startsAt())) {
      throw conflict("INVALID_INTERVAL", "Конец записи должен быть позже начала");
    }
    if (!practitioners.existsById(request.practitionerId())) {
      throw notFound("Врач не найден");
    }
    if (appointments.countOverlaps(request.practitionerId(), request.startsAt(), request.endsAt())
        > 0) {
      throw conflict("SLOT_ALREADY_BOOKED", "Интервал врача уже занят");
    }
    try {
      var appointment =
          appointments.saveAndFlush(
              new Appointment(
                  request.patientId(),
                  request.practitionerId(),
                  request.startsAt(),
                  request.endsAt(),
                  request.serviceName()));
      audit.record(
          actorId(), "APPOINTMENT_CREATED", "APPOINTMENT", appointment.id, "ALLOWED", "booking");
      outbox.enqueue(
          "NOTIFICATION",
          "APPOINTMENT",
          appointment.id,
          "{\"template\":\"appointment-confirmed\",\"neutral\":true}");
      return view(appointment);
    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
      throw conflict("SLOT_ALREADY_BOOKED", "Интервал врача уже занят");
    }
  }

  private AppointmentView view(Appointment a) {
    return new AppointmentView(
        a.id,
        a.patientId,
        a.practitionerId,
        a.startsAt,
        a.endsAt,
        a.serviceName,
        a.status,
        a.version);
  }
}
