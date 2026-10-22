package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Справочник врачей с рабочими часами. */
@Service
class PractitionerService {
  private final PractitionerRepository practitioners;
  private final PractitionerScheduleRepository workingHours;

  PractitionerService(
      PractitionerRepository practitioners, PractitionerScheduleRepository workingHours) {
    this.practitioners = practitioners;
    this.workingHours = workingHours;
  }

  @Transactional(readOnly = true)
  List<PractitionerView> list() {
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
}
