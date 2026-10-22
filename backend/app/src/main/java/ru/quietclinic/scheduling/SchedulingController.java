package ru.quietclinic.scheduling;

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

/** HTTP-слой расписания: права и правила — в {@link AppointmentService}. */
@RestController
@RequestMapping("/api/v1")
public class SchedulingController {
  private final AppointmentService appointments;
  private final PractitionerService practitioners;

  SchedulingController(AppointmentService appointments, PractitionerService practitioners) {
    this.appointments = appointments;
    this.practitioners = practitioners;
  }

  @GetMapping("/practitioners")
  List<PractitionerView> practitioners() {
    return practitioners.list();
  }

  /** {@code ?date=} — один день; {@code ?from=&to=} — период, например история пациента. */
  @GetMapping("/appointments")
  List<AppointmentView> find(
      @RequestParam(required = false) LocalDate date,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) UUID practitionerId,
      @RequestParam(required = false) String status) {
    return appointments.find(
        new AppointmentQuery(date, from, to, patientId, practitionerId, status));
  }

  @PostMapping("/appointments")
  @ResponseStatus(HttpStatus.CREATED)
  AppointmentView create(@Valid @RequestBody CreateAppointmentRequest request) {
    return appointments.create(request);
  }

  @PostMapping("/appointments/{id}/reschedule")
  AppointmentView reschedule(
      @PathVariable UUID id, @Valid @RequestBody RescheduleRequest request) {
    return appointments.reschedule(id, request);
  }

  @PostMapping("/appointments/{id}/cancel")
  AppointmentView cancel(@PathVariable UUID id) {
    return appointments.cancel(id);
  }

  @PostMapping("/appointments/{id}/status")
  AppointmentView changeStatus(
      @PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
    return appointments.changeStatus(id, request.status());
  }
}
