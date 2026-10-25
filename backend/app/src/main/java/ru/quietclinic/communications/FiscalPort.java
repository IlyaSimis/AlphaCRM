package ru.quietclinic.communications;

import java.util.UUID;

/**
 * Порт онлайн-кассы для чека. Реализует модуль оплаты; Telegram и вебхук пациента здесь не
 * участвуют. Пока касса не подключена, порт отвечает {@link FiscalResult#notConfigured()}.
 */
public interface FiscalPort {
  FiscalResult sendReceipt(UUID paymentId, String idempotencyKey);
}
