package ru.quietclinic.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StockDocumentRepository extends JpaRepository<StockDocument, UUID> {
  List<StockDocument> findTop200ByClinicIdOrderByCreatedAtDesc(UUID clinicId);

  Optional<StockDocument> findByIdAndClinicId(UUID id, UUID clinicId);

  Optional<StockDocument> findByClinicIdAndIdempotencyKey(UUID clinicId, String idempotencyKey);
}
