package ru.quietclinic.registry;

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

@RestController
@RequestMapping("/api/v1/registry")
public class RegistryController {
  private final RegistryService registry;

  RegistryController(RegistryService registry) {
    this.registry = registry;
  }

  @GetMapping("/tasks")
  List<TaskView> tasks(
      @RequestParam(required = false) LocalDate dueTo,
      @RequestParam(required = false) UUID patientId) {
    return registry.tasks(dueTo, patientId);
  }

  @PostMapping("/tasks")
  @ResponseStatus(HttpStatus.CREATED)
  TaskView createTask(@Valid @RequestBody CreateTaskRequest request) {
    return registry.createTask(request);
  }

  @PostMapping("/tasks/{id}/done")
  TaskView done(@PathVariable UUID id) {
    return registry.setDone(id, true);
  }

  @PostMapping("/tasks/{id}/reopen")
  TaskView reopen(@PathVariable UUID id) {
    return registry.setDone(id, false);
  }

  @GetMapping("/waitlist")
  List<WaitlistView> waitlist(
      @RequestParam(required = false) UUID practitionerId,
      @RequestParam(required = false) LocalDate date) {
    return registry.waitlist(practitionerId, date);
  }

  @PostMapping("/waitlist")
  @ResponseStatus(HttpStatus.CREATED)
  WaitlistView addToWaitlist(@Valid @RequestBody CreateWaitlistRequest request) {
    return registry.addToWaitlist(request);
  }

  @PostMapping("/waitlist/{id}/book")
  WaitlistView book(@PathVariable UUID id, @Valid @RequestBody BookWaitlistRequest request) {
    return registry.book(id, request);
  }

  @PostMapping("/waitlist/{id}/cancel")
  WaitlistView cancel(@PathVariable UUID id) {
    return registry.cancel(id);
  }
}
