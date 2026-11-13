package ru.quietclinic.billing;

/** Порт драйвера банка/поставщика терминала. Реальный протокол задаёт только его адаптер. */
interface TerminalPaymentGateway {
  boolean configured();

  /** Возвращает внешний идентификатор операции после приёма команды терминалом. */
  String start(TerminalPaymentCommand command);
}
