package ru.quietclinic.clinical;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, UUID> {
  Optional<ClinicalNote> findByEncounterId(UUID encounterId);
}
