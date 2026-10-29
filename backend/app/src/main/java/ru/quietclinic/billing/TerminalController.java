package ru.quietclinic.billing;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TerminalController {
  private final TerminalPaymentService terminalPayments;

  TerminalController(TerminalPaymentService terminalPayments) {
    this.terminalPayments = terminalPayments;
  }

  @PostMapping("/api/v1/billing/charges/{chargeId}/terminal-payments")
  @ResponseStatus(HttpStatus.ACCEPTED)
  BillingView start(
      @PathVariable UUID chargeId, @Valid @RequestBody TerminalPaymentRequest request) {
    return terminalPayments.start(chargeId, request);
  }

  @PostMapping("/api/v1/integrations/terminal/events")
  TerminalEventView event(@Valid @RequestBody TerminalEventRequest request) {
    return terminalPayments.event(request);
  }
}
