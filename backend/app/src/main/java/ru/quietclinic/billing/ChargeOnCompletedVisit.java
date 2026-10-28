package ru.quietclinic.billing;

import java.util.Set;
import org.springframework.stereotype.Component;
import ru.quietclinic.platform.DomainEvent;
import ru.quietclinic.platform.DomainEventHandler;

/**
 * Потребитель {@code AppointmentCompleted}: счёт на завершённый приём. Раньше расписание вызывало
 * {@link ChargeService} напрямую; теперь касса узнаёт о приёме из события (inbox по eventId).
 */
@Component
class ChargeOnCompletedVisit implements DomainEventHandler {
  private final ChargeService charges;

  ChargeOnCompletedVisit(ChargeService charges) {
    this.charges = charges;
  }

  @Override
  public String consumer() {
    return "billing.charge-on-completed";
  }

  @Override
  public Set<String> types() {
    return Set.of("AppointmentCompleted");
  }

  @Override
  public void handle(DomainEvent event) {
    var mode = event.text("priceMode");
    var appointmentId = event.uuid("appointmentId");
    var currency = event.text("currency") == null ? "RUB" : event.text("currency");
    if ("OPEN".equals(mode)) {
      charges.awaitAmountForCompletedVisit(
          appointmentId,
          currency,
          event.number("minMinor"),
          event.number("maxMinor"),
          event.number("estimateMinor"));
    } else if (event.number("amountMinor") != null) {
      charges.chargeForCompletedVisit(appointmentId, event.number("amountMinor"), currency);
    }
  }
}
