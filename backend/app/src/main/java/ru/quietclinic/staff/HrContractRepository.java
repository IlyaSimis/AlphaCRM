package ru.quietclinic.staff;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface HrContractRepository extends JpaRepository<HrContract, UUID> {
  List<HrContract> findByStaffIdOrderByValidFromDesc(UUID staffId);

  List<HrContract> findByClinicIdAndValidToIsNull(UUID clinicId);
}
