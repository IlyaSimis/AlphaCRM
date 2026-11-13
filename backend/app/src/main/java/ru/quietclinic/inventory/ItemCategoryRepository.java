package ru.quietclinic.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ItemCategoryRepository extends JpaRepository<ItemCategory, UUID> {
  List<ItemCategory> findByClinicIdOrderByName(UUID clinicId);

  Optional<ItemCategory> findByIdAndClinicId(UUID id, UUID clinicId);

  boolean existsByClinicIdAndNameIgnoreCaseAndParentId(UUID clinicId, String name, UUID parentId);
}
