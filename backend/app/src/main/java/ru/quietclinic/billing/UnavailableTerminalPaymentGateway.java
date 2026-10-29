package ru.quietclinic.billing;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import org.springframework.stereotype.Component;

/** Без драйвера банка интерфейс честно сообщает, что терминал не подключён. */
@Component
class UnavailableTerminalPaymentGateway implements TerminalPaymentGateway {
  @Override
  public boolean configured() {
    return false;
  }

  @Override
  public String start(TerminalPaymentCommand command) {
    throw conflict(
        "TERMINAL_NOT_CONFIGURED", "Connect the bank or payment terminal provider first");
  }
}
