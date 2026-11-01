package ru.quietclinic.clinical;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP-слой клинического контура: права и правила — в {@link EncounterService}. */
@RestController
@RequestMapping("/api/v1/clinical")
public class ClinicalController {
  private final EncounterService encounters;

  ClinicalController(EncounterService encounters) {
    this.encounters = encounters;
  }

  /** 201 — новый приём, 200 — уже начатый приём по той же записи. */
  @PostMapping("/encounters")
  ResponseEntity<ClinicalView> start(@Valid @RequestBody StartEncounterRequest request) {
    EncounterStart started = encounters.start(request);
    return ResponseEntity.status(started.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(started.view());
  }

  @GetMapping("/patients/{patientId}/encounters")
  List<ClinicalView> history(@PathVariable UUID patientId) {
    return encounters.history(patientId);
  }

  @PutMapping("/encounters/{encounterId}/note")
  ClinicalView saveNote(
      @PathVariable UUID encounterId, @Valid @RequestBody ClinicalNoteRequest request) {
    return encounters.saveNote(encounterId, request);
  }

  @PostMapping("/encounters/{encounterId}/documents")
  @ResponseStatus(HttpStatus.CREATED)
  DocumentView createDocument(
      @PathVariable UUID encounterId, @Valid @RequestBody DocumentRequest request) {
    return encounters.createDocument(encounterId, request);
  }

  @PostMapping("/documents/{documentId}/sign")
  DocumentView sign(@PathVariable UUID documentId) {
    return encounters.sign(documentId);
  }

  @PostMapping("/documents/{documentId}/amendments")
  @ResponseStatus(HttpStatus.CREATED)
  DocumentView amend(@PathVariable UUID documentId, @Valid @RequestBody DocumentRequest request) {
    return encounters.amend(documentId, request);
  }
}
