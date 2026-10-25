package ru.quietclinic.patients;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Административные поля регистратуры. Пустое значение источника и заметки очищает поле.
 *
 * @param telegramChatId chat id Telegram пациента (только цифры); пусто или null — не менять
 * @param removeTelegram true — отключить сообщения в Telegram
 */
record RegistryRequest(
    String referralSource,
    @Size(max = 1000) String registryNote,
    @Pattern(regexp = "^\\s*(-?\\d{1,20})?\\s*$") String telegramChatId,
    Boolean removeTelegram) {}
