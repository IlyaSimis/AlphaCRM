package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;

/** Справочник врачей клиники с недельным графиком. */
@Service
class PractitionerService {
  private final PractitionerRepository practitioners;
  private final PractitionerScheduleRepository workingHours;
  private final AuditService audit;

  PractitionerService(
      PractitionerRepository practitioners,
      PractitionerScheduleRepository workingHours,
      AuditService audit) {
    this.practitioners = practitioners;
    this.workingHours = workingHours;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  List<PractitionerView> list() {
    requireRole("ADMINISTRATOR", "DOCTOR", "NURSE", "CASHIER", "MANAGER");
    return practitioners.findByClinicIdOrderByFullName(clinicId()).stream()
        .map(this::view)
        .toList();
  }

  /**
   * Заменяет недельный график. Уже созданные записи не трогает: они остаются как были, новые
   * проверяются по новому графику.
   */
  @Transactional
  PractitionerView replaceWorkingHours(UUID practitionerId, WorkingHoursRequest request) {
    requireRole("ADMINISTRATOR");
    var practitioner =
        practitioners
            .findByIdAndClinicId(practitionerId, clinicId())
            .orElseThrow(() -> notFound("PRACTITIONER_NOT_FOUND", "Practitioner not found"));
    var days = new HashSet<Integer>();
    for (var shift : request.shifts()) {
      if (!days.add(shift.dayOfWeek())) {
        throw conflict("INVALID_SHIFT", "A weekday is listed twice");
      }
      if (!shift.endsAt().isAfter(shift.startsAt())) {
        throw conflict("INVALID_SHIFT", "Shift end must be after its start");
      }
    }
    workingHours.deleteByPractitionerId(practitionerId);
    workingHours.flush();
    workingHours.saveAll(
        request.shifts().stream()
            .map(
                s ->
                    new PractitionerSchedule(
                        practitionerId, s.dayOfWeek(), s.startsAt(), s.endsAt()))
            .toList());
    var reason =
        "days:"
            + request.shifts().stream()
                .map(ShiftRequest::dayOfWeek)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    audit.record(
        actorId(), "PRACTITIONER_HOURS_CHANGED", "PRACTITIONER", practitionerId, "ALLOWED", reason);
    return view(practitioner);
  }

  private PractitionerView view(Practitioner p) {
    return new PractitionerView(
        p.id,
        p.fullName,
        p.specialty,
        workingHours.findByPractitionerIdOrderByDayOfWeek(p.id).stream()
            .map(h -> new WorkingHoursView(h.dayOfWeek, h.startsAt, h.endsAt))
            .toList());
  }
}
