package ru.quietclinic.clinical;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EncounterRepository extends JpaRepository<Encounter, UUID> {
  List<Encounter> findByPatientIdOrderByStartedAtDesc(UUID patientId);

  Optional<Encounter> findFirstByAppointmentIdOrderByStartedAtAsc(UUID appointmentId);
}
