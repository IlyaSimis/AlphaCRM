package ru.quietclinic.billing;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Платёжный шлюз не подключён: оплата только отмечается в кассе. Статус MOCK_NO_PROVIDER честно
 * говорит, что деньги через систему не проводились.
 */
@Component
class SafeMockPaymentGateway implements PaymentGateway {
  static final String STATUS = "MOCK_NO_PROVIDER";

  public ProviderPayment capture(
      UUID chargeId, long amountMinor, String currency, String idempotencyKey) {
    return new ProviderPayment("mock-pay-" + chargeId, STATUS);
  }
}
