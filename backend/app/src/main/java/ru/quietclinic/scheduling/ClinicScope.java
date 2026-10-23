package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.clinicId;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Границы клиники сотрудника для расписания: записи принадлежат клинике через своего врача. Пациент
 * проверяется запросом к таблице — без зависимости от кода модуля patients.
 */
@Component
class ClinicScope {
  private final PractitionerRepository practitioners;
  private final JdbcTemplate jdbc;

  ClinicScope(PractitionerRepository practitioners, JdbcTemplate jdbc) {
    this.practitioners = practitioners;
    this.jdbc = jdbc;
  }

  Set<UUID> practitionerIds() {
    return practitioners.findByClinicIdOrderByFullName(clinicId()).stream()
        .map(p -> p.id)
        .collect(Collectors.toSet());
  }

  boolean hasPractitioner(UUID practitionerId) {
    return practitionerId != null
        && practitioners.existsByIdAndClinicId(practitionerId, clinicId());
  }

  boolean hasPatient(UUID patientId) {
    var count =
        jdbc.queryForObject(
            "select count(*) from patient where id = ? and clinic_id = ?",
            Long.class,
            patientId,
            clinicId());
    return count != null && count > 0;
  }

  boolean hasAppointment(Appointment appointment) {
    return hasPractitioner(appointment.practitionerId);
  }
}
