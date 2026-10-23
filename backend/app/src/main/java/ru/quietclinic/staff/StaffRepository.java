package ru.quietclinic.staff;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StaffRepository extends JpaRepository<StaffUser, UUID> {
  Optional<StaffUser> findByUsername(String username);

  boolean existsByUsername(String username);

  boolean existsByPractitionerId(UUID practitionerId);

  List<StaffUser> findByClinicIdOrderByFullName(UUID clinicId);

  Optional<StaffUser> findByIdAndClinicId(UUID id, UUID clinicId);
}
