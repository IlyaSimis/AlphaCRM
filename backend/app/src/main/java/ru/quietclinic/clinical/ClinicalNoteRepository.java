package ru.quietclinic.clinical;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, UUID> {
  java.util.Optional<ClinicalNote> findByEncounterId(UUID encounterId);
}
