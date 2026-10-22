package ru.quietclinic.clinical;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MedicalDocumentRepository extends JpaRepository<MedicalDocument, UUID> {
  List<MedicalDocument> findByEncounterIdOrderByCreatedAtAsc(UUID encounterId);
}
