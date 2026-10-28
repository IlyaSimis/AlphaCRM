package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.platform.ClinicTime;

/**
 * Онлайн-запись для Telegram-бота: врачи с графиком, свободные окна и запись пациента на одну
 * услугу из справочника ({@code clinic.online-booking.service-name}). Вызывающий — сервисная
 * учётная запись бота с ролью BOT, клиника — из неё же.
 */
@Service
public class OnlineBooking {
  static final String ROLE = "BOT";

  private final PractitionerRepository practitioners;
  private final PractitionerScheduleRepository schedules;
  private final ClinicServiceRepository services;
  private final AppointmentRepository appointments;
  private final AppointmentService appointmentService;
  private final ClinicTime time;
  private final String serviceName;
  private final Duration slotStep;
  private final int days;
  private final Duration minLead;
  private final int maxSlots;

  OnlineBooking(
      PractitionerRepository practitioners,
      PractitionerScheduleRepository schedules,
      ClinicServiceRepository services,
      AppointmentRepository appointments,
      AppointmentService appointmentService,
      ClinicTime time,
      @Value("${clinic.online-booking.service-name:}") String serviceName,
      @Value("${clinic.online-booking.slot-step:PT30M}") Duration slotStep,
      @Value("${clinic.online-booking.days:14}") int days,
      @Value("${clinic.online-booking.min-lead:PT2H}") Duration minLead,
      @Value("${clinic.online-booking.max-slots:60}") int maxSlots) {
    this.practitioners = practitioners;
    this.schedules = schedules;
    this.services = services;
    this.appointments = appointments;
    this.appointmentService = appointmentService;
    this.time = time;
    this.serviceName = serviceName == null ? "" : serviceName.trim();
    this.slotStep = slotStep;
    this.days = days;
    this.minLead = minLead;
    this.maxSlots = maxSlots;
  }

  /** Название услуги онлайн-записи — его видит пациент в боте. */
  public String serviceName() {
    requireRole(ROLE);
    return onlineService().name;
  }

  /** Врачи клиники, у которых есть хотя бы одна смена: к остальным записаться нельзя. */
  @Transactional(readOnly = true)
  public List<BookablePractitioner> practitioners() {
    requireRole(ROLE);
    return practitioners.findByClinicIdOrderByFullName(clinicId()).stream()
        .filter(p -> !schedules.findByPractitionerIdOrderByDayOfWeek(p.id).isEmpty())
        .map(BookablePractitioner::of)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<BookablePractitioner> practitioner(UUID id) {
    requireRole(ROLE);
    return practitioners.findByIdAndClinicId(id, clinicId()).map(BookablePractitioner::of);
  }

  /**
   * Свободные окна врача на {@code days} дней вперёд, начиная не раньше чем через {@code min-lead}.
   * Окно — длительность услуги онлайн-записи; начала идут с шагом {@code slot-step} от начала
   * смены. Занятое время — те же статусы, что считает проверка пересечения при записи.
   */
  @Transactional(readOnly = true)
  public List<FreeSlot> freeSlots(UUID practitionerId) {
    requireRole(ROLE);
    if (practitioners.findByIdAndClinicId(practitionerId, clinicId()).isEmpty()) {
      throw notFound("PRACTITIONER_NOT_FOUND", "Practitioner not found");
    }
    var duration = Duration.ofMinutes(onlineService().durationMinutes);
    var shifts = schedules.findByPractitionerIdOrderByDayOfWeek(practitionerId);
    var zone = time.zone();
    var today = time.today();
    var earliest = time.now().plus(minLead);
    var busy =
        appointments.findBusy(
            practitionerId, time.startOf(today), time.startOf(today.plusDays(days + 1L)));
    var result = new ArrayList<FreeSlot>();
    for (var offset = 0; offset < days && result.size() < maxSlots; offset++) {
      var day = today.plusDays(offset);
      var dayOfWeek = day.getDayOfWeek().getValue();
      for (var shift : shifts) {
        if (shift.dayOfWeek != dayOfWeek) continue;
        var shiftEnd = day.atTime(shift.endsAt);
        for (var start = day.atTime(shift.startsAt);
            !start.plus(duration).isAfter(shiftEnd) && result.size() < maxSlots;
            start = start.plus(slotStep)) {
          var startsAt = start.atZone(zone).toInstant();
          var endsAt = startsAt.plus(duration);
          if (startsAt.isBefore(earliest) || overlaps(busy, startsAt, endsAt)) continue;
          result.add(new FreeSlot(startsAt, endsAt));
        }
      }
    }
    return result;
  }

  /** Запись пациента на начало окна; конец — по длительности услуги. */
  public OnlineAppointment book(UUID patientId, UUID practitionerId, Instant startsAt) {
    requireRole(ROLE);
    return OnlineAppointment.of(
        appointmentService.createOnline(patientId, practitionerId, startsAt, onlineService().name));
  }

  @Transactional(readOnly = true)
  public Optional<OnlineAppointment> appointment(UUID id) {
    requireRole(ROLE);
    return appointmentService.findView(id).map(OnlineAppointment::of);
  }

  public OnlineAppointment confirm(UUID id) {
    requireRole(ROLE);
    return OnlineAppointment.of(appointmentService.confirmByPatient(id));
  }

  public OnlineAppointment cancel(UUID id) {
    requireRole(ROLE);
    return OnlineAppointment.of(appointmentService.cancelByPatient(id));
  }

  private ClinicService onlineService() {
    if (serviceName.isEmpty()) {
      throw conflict(
          "ONLINE_SERVICE_NOT_CONFIGURED", "Set CLINIC_BOT_SERVICE to a service from the catalog");
    }
    var service =
        services
            .findByClinicIdAndName(clinicId(), serviceName)
            .orElseThrow(
                () -> conflict("ONLINE_SERVICE_NOT_FOUND", "Online booking service not found"));
    if (!service.active) {
      throw conflict("ONLINE_SERVICE_INACTIVE", "Online booking service is switched off");
    }
    return service;
  }

  private static boolean overlaps(List<Appointment> busy, Instant startsAt, Instant endsAt) {
    for (var a : busy) {
      if (a.startsAt.isBefore(endsAt) && a.endsAt.isAfter(startsAt)) return true;
    }
    return false;
  }
}
