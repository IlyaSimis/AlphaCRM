package ru.quietclinic.inventory;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface StockLineRepository extends JpaRepository<StockLine, UUID> {
  List<StockLine> findByDocumentIdOrderByLineNo(UUID documentId);

  void deleteByDocumentId(UUID documentId);
}
