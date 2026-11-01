package ru.quietclinic.inventory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ItemRepository extends JpaRepository<Item, UUID> {
  List<Item> findByClinicIdOrderByName(UUID clinicId);

  Optional<Item> findByIdAndClinicId(UUID id, UUID clinicId);

  List<Item> findByClinicIdAndGtin(UUID clinicId, String gtin);

  List<Item> findByClinicIdAndIdIn(UUID clinicId, Collection<UUID> ids);
}
