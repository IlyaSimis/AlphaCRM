package ru.quietclinic.commercial;

import java.util.UUID;

/** Порт hosted checkout подписки. Данные банковской карты через AlphaCRM не проходят. */
public interface CommercialPaymentGateway {
  boolean enabled();

  ProviderCheckout createCheckout(PaymentCommand command);

  ProviderPayment getPayment(String providerRef);

  record PaymentCommand(
      UUID paymentId, UUID clinicId, String planName, long amountMinor, String currency) {}

  record ProviderCheckout(String providerRef, String checkoutUrl) {}

  record ProviderPayment(
      String providerRef,
      String status,
      boolean paid,
      long amountMinor,
      String currency,
      UUID paymentId) {}
}
