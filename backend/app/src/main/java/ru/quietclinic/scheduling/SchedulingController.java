package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
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
        and a.status in ('BOOKED','CONFIRMED','ARRIVED','IN_PROGRESS')
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

record CreateAppointmentRequest(
    @NotNull UUID patientId,
    @NotNull UUID practitionerId,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @NotNull String serviceName) {}

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
  private final AuditService audit;
  private final OutboxService outbox;

  SchedulingController(
      AppointmentRepository appointments, AuditService audit, OutboxService outbox) {
    this.appointments = appointments;
    this.audit = audit;
    this.outbox = outbox;
  }

  @GetMapping("/appointments")
  @Transactional(readOnly = true)
  List<AppointmentView> schedule(@RequestParam LocalDate date) {
    requireRole("REGISTRAR", "DOCTOR", "NURSE", "CASHIER", "MANAGER");
    var from = date.atStartOfDay().toInstant(ZoneOffset.UTC);
    return appointments
        .findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
            from, date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))
        .stream()
        .map(this::view)
        .toList();
  }

  @PostMapping("/appointments")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  AppointmentView create(@Valid @RequestBody CreateAppointmentRequest request) {
    requireRole("REGISTRAR", "DOCTOR");
    if (!request.endsAt().isAfter(request.startsAt())) {
      throw conflict("INVALID_INTERVAL", "Конец записи должен быть позже начала");
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
