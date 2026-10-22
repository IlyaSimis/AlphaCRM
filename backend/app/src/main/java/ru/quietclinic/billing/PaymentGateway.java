package ru.quietclinic.billing;

import java.util.UUID;

interface PaymentGateway {
  ProviderPayment capture(UUID chargeId, long amountMinor, String currency, String idempotencyKey);
}
