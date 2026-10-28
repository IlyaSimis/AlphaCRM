package ru.quietclinic.scheduling;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ServiceNormRepository extends JpaRepository<ServiceNorm, UUID> {
  List<ServiceNorm> findByServiceId(UUID serviceId);

  void deleteByServiceId(UUID serviceId);
}
