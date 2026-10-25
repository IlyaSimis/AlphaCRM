package ru.quietclinic.communications;

/** Итог попытки: что вышло, через какой канал и машинный код причины (без текста и адреса). */
record DeliveryResult(DeliveryOutcome outcome, String channel, String reason) {
  static DeliveryResult sent(String channel) {
    return new DeliveryResult(DeliveryOutcome.SENT, channel, null);
  }

  static DeliveryResult notConfigured(String channel, String reason) {
    return new DeliveryResult(DeliveryOutcome.NOT_CONFIGURED, channel, reason);
  }

  static DeliveryResult retry(String channel, String reason) {
    return new DeliveryResult(DeliveryOutcome.RETRY, channel, reason);
  }

  static DeliveryResult rejected(String channel, String reason) {
    return new DeliveryResult(DeliveryOutcome.REJECTED, channel, reason);
  }

  /** Код по HTTP-статусу ответа: 2xx — доставлено, 4xx — отказ, остальное — повтор. */
  static DeliveryResult ofHttp(String channel, int status) {
    if (status >= 200 && status < 300) return sent(channel);
    if (status >= 400 && status < 500 && status != 408 && status != 429) {
      return rejected(channel, "HTTP_" + status);
    }
    return retry(channel, "HTTP_" + status);
  }
}
