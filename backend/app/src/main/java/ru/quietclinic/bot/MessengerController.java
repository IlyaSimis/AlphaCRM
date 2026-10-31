package ru.quietclinic.bot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
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
import ru.quietclinic.patients.MessengerChannel;
import ru.quietclinic.patients.PatientMessengerLinks;

/** Общий приватный API Telegram и MAX. Доступен только по X-Bot-Token через внутреннюю сеть/VPN. */
@RestController
@RequestMapping("/api/v1/messenger")
class MessengerController {
  private final BotService bot;
  private final PatientMessengerLinks links;

  MessengerController(BotService bot, PatientMessengerLinks links) {
    this.bot = bot;
    this.links = links;
  }

  @GetMapping("/clinic")
  BotClinicView clinic() { return bot.clinic(); }

  @GetMapping("/practitioners")
  List<BotPractitionerView> practitioners() { return bot.practitioners(); }

  @GetMapping("/practitioners/{id}/slots")
  List<BotSlotView> slots(@PathVariable UUID id) { return bot.slots(id); }

  @GetMapping("/patients/{channel}/{externalId}")
  BotPatientView patient(
      @PathVariable String channel,
      @PathVariable @Pattern(regexp = "^[A-Za-z0-9_-]{1,128}$") String externalId) {
    return bot.patient(MessengerChannel.parse(channel), externalId);
  }

  @PostMapping("/link/consume")
  BotPatientView consume(@Valid @RequestBody MessengerLinkConsumeRequest request) {
    var patient = links.consume(MessengerChannel.parse(request.channel()), request.externalId(), request.token());
    return new BotPatientView(patient.id(), patient.fullName(), patient.birthDate(), patient.phone());
  }

  @PostMapping("/appointments")
  @ResponseStatus(HttpStatus.CREATED)
  BotAppointmentView book(@Valid @RequestBody MessengerBookingRequest request) {
    return bot.book(request);
  }

  @GetMapping("/appointments")
  List<BotAppointmentView> appointments(@RequestParam String channel, @RequestParam String externalId) {
    return bot.appointments(MessengerChannel.parse(channel), externalId);
  }

  @GetMapping("/appointments/{id}")
  BotAppointmentView appointment(
      @PathVariable UUID id, @RequestParam String channel, @RequestParam String externalId) {
    return bot.appointment(id, MessengerChannel.parse(channel), externalId);
  }

  @PostMapping("/appointments/{id}/confirm")
  BotAppointmentView confirm(@PathVariable UUID id, @Valid @RequestBody MessengerIdentityRequest request) {
    return bot.confirm(id, MessengerChannel.parse(request.channel()), request.externalId());
  }

  @PostMapping("/appointments/{id}/cancel")
  BotAppointmentView cancel(@PathVariable UUID id, @Valid @RequestBody MessengerIdentityRequest request) {
    return bot.cancel(id, MessengerChannel.parse(request.channel()), request.externalId());
  }

  @PostMapping("/operator-requests")
  @ResponseStatus(HttpStatus.CREATED)
  BotTicketView operator(@Valid @RequestBody MessengerOperatorRequest request) {
    return bot.operatorRequest(request);
  }
}
