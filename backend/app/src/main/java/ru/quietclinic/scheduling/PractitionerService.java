package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.Permission;

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
    requirePermission(Permission.PRACTITIONER_READ);
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
    requirePermission(Permission.PRACTITIONER_MANAGE);
    var practitioner =
        practitioners
            .findByIdAndClinicId(practitionerId, clinicId())
            .orElseThrow(() -> notFound("PRACTITIONER_NOT_FOUND", "Practitioner not found"));
    validate(request.shifts());
    workingHours.deleteByPractitionerId(practitionerId);
    workingHours.flush();
    saveShifts(practitionerId, request.shifts());
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

  /**
   * Новая карточка врача вместе с недельным графиком. Пустой график допустим: карточка есть, но
   * записать к врачу нельзя, пока не задана смена.
   */
  @Transactional
  PractitionerView create(CreatePractitionerRequest request) {
    requirePermission(Permission.PRACTITIONER_MANAGE);
    validate(request.shifts());
    var practitioner =
        practitioners.saveAndFlush(
            new Practitioner(
                clinicId(),
                request.fullName().trim(),
                request.specialty().trim(),
                Practitioner.roomOrNull(request.room())));
    saveShifts(practitioner.id, request.shifts());
    // Сотрудники проверяют карточку через JDBC в этой же транзакции — строки должны быть в базе.
    workingHours.flush();
    var fields = new ArrayList<>(List.of("fullName", "specialty"));
    if (practitioner.room != null) fields.add("room");
    if (!request.shifts().isEmpty()) fields.add("shifts");
    audit.record(
        actorId(),
        "PRACTITIONER_CREATED",
        "PRACTITIONER",
        practitioner.id,
        "ALLOWED",
        "fields:" + String.join(",", fields));
    return view(practitioner);
  }

  /** Специальность и кабинет существующего врача; график меняется отдельно. */
  @Transactional
  PractitionerView update(UUID practitionerId, UpdatePractitionerRequest request) {
    requirePermission(Permission.PRACTITIONER_MANAGE);
    var practitioner =
        practitioners
            .findByIdAndClinicId(practitionerId, clinicId())
            .orElseThrow(() -> notFound("PRACTITIONER_NOT_FOUND", "Practitioner not found"));
    var specialty = request.specialty().trim();
    var room = Practitioner.roomOrNull(request.room());
    var fields = new ArrayList<String>();
    if (!specialty.equals(practitioner.specialty)) fields.add("specialty");
    if (!Objects.equals(room, practitioner.room)) fields.add("room");
    practitioner.specialty = specialty;
    practitioner.room = room;
    if (!fields.isEmpty()) {
      audit.record(
          actorId(),
          "PRACTITIONER_UPDATED",
          "PRACTITIONER",
          practitioner.id,
          "ALLOWED",
          "fields:" + String.join(",", fields));
    }
    return view(practitioner);
  }

  private static void validate(List<ShiftRequest> shifts) {
    var days = new HashSet<Integer>();
    for (var shift : shifts) {
      if (!days.add(shift.dayOfWeek())) {
        throw conflict("INVALID_SHIFT", "A weekday is listed twice");
      }
      if (!shift.endsAt().isAfter(shift.startsAt())) {
        throw conflict("INVALID_SHIFT", "Shift end must be after its start");
      }
    }
  }

  private void saveShifts(UUID practitionerId, List<ShiftRequest> shifts) {
    workingHours.saveAll(
        shifts.stream()
            .map(
                s ->
                    new PractitionerSchedule(
                        practitionerId, s.dayOfWeek(), s.startsAt(), s.endsAt()))
            .toList());
  }

  private PractitionerView view(Practitioner p) {
    return new PractitionerView(
        p.id,
        p.fullName,
        p.specialty,
        p.room,
        workingHours.findByPractitionerIdOrderByDayOfWeek(p.id).stream()
            .map(h -> new WorkingHoursView(h.dayOfWeek, h.startsAt, h.endsAt))
            .toList());
  }
}
