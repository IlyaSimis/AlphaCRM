package ru.quietclinic.patients;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP-слой пациентов: права и аудит — в {@link PatientService}. */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientsController {
  private final PatientService patients;

  PatientsController(PatientService patients) {
    this.patients = patients;
  }

  @GetMapping
  List<PatientView> search(@RequestParam(defaultValue = "") String query) {
    return patients.search(query);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  PatientView create(@Valid @RequestBody CreatePatientRequest request) {
    return patients.create(request);
  }

  @GetMapping("/{id}")
  PatientView get(@PathVariable UUID id) {
    return patients.get(id);
  }

  @PutMapping("/{id}")
  PatientView update(@PathVariable UUID id, @Valid @RequestBody UpdatePatientRequest request) {
    return patients.update(id, request);
  }

  @PostMapping("/{id}/consents")
  @ResponseStatus(HttpStatus.CREATED)
  PatientView grantConsent(@PathVariable UUID id, @Valid @RequestBody ConsentRequest request) {
    return patients.grantConsent(id, request);
  }

  @PostMapping("/{id}/consents/{consentId}/revoke")
  PatientView revokeConsent(@PathVariable UUID id, @PathVariable UUID consentId) {
    return patients.revokeConsent(id, consentId);
  }
}
