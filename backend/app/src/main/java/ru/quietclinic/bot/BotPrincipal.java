package ru.quietclinic.bot;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;
import ru.quietclinic.platform.ClinicMember;

/**
 * Сервисная учётная запись Telegram-бота. Автор действий в аудите — сама клиника, как у доставщика
 * outbox: журнал покажет название клиники и основание «telegram».
 */
record BotPrincipal(UUID clinicId) implements Principal, Serializable, ClinicMember {
  @Override
  public String getName() {
    return clinicId.toString();
  }
}
