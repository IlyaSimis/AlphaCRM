package ru.quietclinic.billing;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class SafeMockPaymentGateway implements PaymentGateway {
  public ProviderPayment capture(
      UUID chargeId, long amountMinor, String currency, String idempotencyKey) {
    return new ProviderPayment("mock-pay-" + chargeId, "CONFIRMED");
  }
}
