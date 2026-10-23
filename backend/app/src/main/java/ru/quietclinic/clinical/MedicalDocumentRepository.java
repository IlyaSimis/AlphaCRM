package ru.quietclinic.clinical;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MedicalDocumentRepository extends JpaRepository<MedicalDocument, UUID> {
  List<MedicalDocument> findByEncounterIdOrderByCreatedAtAsc(UUID encounterId);

  /** Архив: только подписанные версии, черновики сюда не попадают. */
  List<MedicalDocument> findByStateOrderBySignedAtDesc(String state);

  List<MedicalDocument> findByStateAndSignedByOrderBySignedAtDesc(String state, UUID signedBy);
}
