package ru.quietclinic.scheduling;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PractitionerScheduleRepository extends JpaRepository<PractitionerSchedule, UUID> {
  List<PractitionerSchedule> findByPractitionerIdOrderByDayOfWeek(UUID practitionerId);

  void deleteByPractitionerId(UUID practitionerId);
}
