package ru.quietclinic.communications;

/**
 * Итог отправки чека в кассу: outcome — SENT, NOT_CONFIGURED, RETRY или REJECTED; reason —
 * машинный код.
 */
public record FiscalResult(String outcome, String reason) {
  public static final String CHANNEL = "CASHBOX";

  public static FiscalResult sent() {
    return new FiscalResult("SENT", null);
  }

  public static FiscalResult notConfigured() {
    return new FiscalResult("NOT_CONFIGURED", "FISCAL_NOT_CONFIGURED");
  }

  public static FiscalResult ofHttp(int status) {
    var result = DeliveryResult.ofHttp(CHANNEL, status);
    return new FiscalResult(result.outcome().name(), result.reason());
  }

  public static FiscalResult retry(String reason) {
    return new FiscalResult("RETRY", reason);
  }

  DeliveryResult asDelivery() {
    return new DeliveryResult(DeliveryOutcome.valueOf(outcome), CHANNEL, reason);
  }
}
