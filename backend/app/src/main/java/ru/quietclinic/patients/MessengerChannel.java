package ru.quietclinic.patients;

import static ru.quietclinic.platform.PlatformConfig.badRequest;

import java.util.Locale;

/** Поддерживаемые мессенджеры. Внешний id хранится как зашифрованный контакт того же имени. */
public enum MessengerChannel {
  TELEGRAM,
  MAX;

  public static MessengerChannel parse(String value) {
    if (value == null) throw badRequest("MESSENGER_CHANNEL_INVALID", "Unknown messenger channel");
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw badRequest("MESSENGER_CHANNEL_INVALID", "Unknown messenger channel");
    }
  }

  public String consentBasis() {
    return name().toLowerCase(Locale.ROOT) + "-bot";
  }
}
