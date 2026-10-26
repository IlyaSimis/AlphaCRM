package ru.quietclinic.bot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Кто нажал кнопку в боте: chat id личного чата с пациентом. */
record BotChatRequest(@NotBlank @Pattern(regexp = CHAT_ID) String telegramChatId) {
  /** Chat id личного чата — положительное целое. */
  static final String CHAT_ID = "^\\d{1,20}$";
}
