package ru.quietclinic.telephony;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Звонки сотрудников и отдельные подписанные уведомления Mango. */
@RestController
public class TelephonyController {
  private final TelephonyService telephony;
  private final MangoRoutingGateway mango;

  TelephonyController(TelephonyService telephony, MangoRoutingGateway mango) {
    this.telephony = telephony;
    this.mango = mango;
  }

  @GetMapping("/api/v1/telephony/status")
  StatusView status() {
    var client = mango.forClinic(ru.quietclinic.platform.PlatformConfig.clinicId());
    return new StatusView(client.configured(), client.defaultExtension());
  }

  @PostMapping("/api/v1/telephony/calls")
  @ResponseStatus(HttpStatus.CREATED)
  CallView call(@Valid @RequestBody StartCallRequest request) {
    return telephony.start(request.patientId(), request.extension());
  }

  @GetMapping("/api/v1/telephony/patients/{patientId}/calls")
  List<CallView> history(@PathVariable UUID patientId) {
    return telephony.history(patientId);
  }

  @PostMapping(
      value = "/api/v1/integrations/mango/events/call",
      consumes = "application/x-www-form-urlencoded")
  EventAck callEvent(@RequestParam String json) {
    telephony.callEvent(json);
    return new EventAck(true);
  }

  @PostMapping(
      value = "/api/v1/integrations/mango/events/{publicKey}/call",
      consumes = "application/x-www-form-urlencoded")
  EventAck scopedCallEvent(@RequestParam String json) {
    telephony.callEvent(json);
    return new EventAck(true);
  }

  @PostMapping(
      value = "/api/v1/integrations/mango/events/recording",
      consumes = "application/x-www-form-urlencoded")
  EventAck recordingEvent(@RequestParam String json) {
    telephony.recordingEvent(json);
    return new EventAck(true);
  }

  @PostMapping(
      value = "/api/v1/integrations/mango/events/{publicKey}/recording",
      consumes = "application/x-www-form-urlencoded")
  EventAck scopedRecordingEvent(@RequestParam String json) {
    telephony.recordingEvent(json);
    return new EventAck(true);
  }

  record StatusView(boolean configured, String defaultExtension) {}

  record StartCallRequest(
      UUID patientId,
      @Pattern(regexp = "^$|^[0-9]{1,32}$", message = "extension must contain digits only")
          String extension) {}

  record CallView(
      UUID id,
      String direction,
      String status,
      String extension,
      Instant occurredAt,
      boolean recordingAvailable) {}

  record EventAck(boolean accepted) {}
}
