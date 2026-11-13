package ru.quietclinic.staff;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface StaffRepository extends JpaRepository<StaffUser, UUID> {
  Optional<StaffUser> findByUsername(String username);

  /** Параллельные входы одного сотрудника выполняются последовательно: активна только последняя. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from StaffUser u where u.username = :username")
  Optional<StaffUser> findForLogin(@Param("username") String username);

  boolean existsByUsername(String username);

  boolean existsByPractitionerId(UUID practitionerId);

  List<StaffUser> findByClinicIdOrderByFullName(UUID clinicId);

  Optional<StaffUser> findByIdAndClinicId(UUID id, UUID clinicId);
}
