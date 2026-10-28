package ru.quietclinic.clinical;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Экстренный доступ владельца к клинической истории пациента и журнал таких доступов. */
@RestController
@RequestMapping("/api/v1")
public class BreakGlassController {
  private final BreakGlassService breakGlass;

  BreakGlassController(BreakGlassService breakGlass) {
    this.breakGlass = breakGlass;
  }

  @PostMapping("/patients/{patientId}/break-glass")
  @ResponseStatus(HttpStatus.CREATED)
  BreakGlassView grant(
      @PathVariable UUID patientId, @Valid @RequestBody BreakGlassRequest request) {
    return breakGlass.grant(patientId, request);
  }

  @GetMapping("/patients/{patientId}/break-glass")
  BreakGlassView current(@PathVariable UUID patientId) {
    return breakGlass.current(patientId);
  }

  @GetMapping("/clinical/break-glass")
  List<BreakGlassView> list(@RequestParam LocalDate from, @RequestParam LocalDate to) {
    return breakGlass.list(from, to);
  }
}
