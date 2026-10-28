package ru.quietclinic.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
  List<Warehouse> findByClinicIdOrderByName(UUID clinicId);

  Optional<Warehouse> findByIdAndClinicId(UUID id, UUID clinicId);
}
