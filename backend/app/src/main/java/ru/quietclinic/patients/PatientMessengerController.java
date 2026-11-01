package ru.quietclinic.patients;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Управление мессенджерами из карточки пациента; права проверяет сервис. */
@RestController
@RequestMapping("/api/v1/patients/{patientId}/messenger")
class PatientMessengerController {
  private final PatientMessengerLinks links;

  PatientMessengerController(PatientMessengerLinks links) {
    this.links = links;
  }

  @PostMapping("/link")
  MessengerLinkView link(
      @PathVariable UUID patientId, @Valid @RequestBody MessengerLinkRequest request) {
    return links.create(patientId, request.channel());
  }

  @PostMapping("/{channel}/disconnect")
  PatientView disconnect(@PathVariable UUID patientId, @PathVariable String channel) {
    return links.disconnect(patientId, channel);
  }

  @PutMapping("/preference")
  PatientView prefer(
      @PathVariable UUID patientId, @Valid @RequestBody MessengerPreferenceRequest request) {
    return links.prefer(patientId, request.channel());
  }
}
