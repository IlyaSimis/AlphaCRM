package ru.quietclinic.communications;

import java.util.UUID;

/**
 * Порт онлайн-кассы для чека. Реализует модуль оплаты; Telegram и вебхук пациента здесь не
 * участвуют. Пока касса не подключена, порт отвечает {@link FiscalResult#notConfigured()}.
 */
public interface FiscalPort {
  FiscalResult sendReceipt(UUID paymentId, String idempotencyKey);

  /**
   * Чек возврата прихода по строке возврата. По умолчанию касса не подключена: порт остаётся
   * функциональным интерфейсом (в тестах — лямбда для чека оплаты).
   */
  default FiscalResult sendRefund(UUID refundId, String idempotencyKey) {
    return FiscalResult.notConfigured();
  }
}
