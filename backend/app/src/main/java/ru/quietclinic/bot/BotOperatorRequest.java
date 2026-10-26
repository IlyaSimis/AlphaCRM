package ru.quietclinic.bot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Пациент попросил оператора: текст задачи для регистратуры собирает бот. */
record BotOperatorRequest(
    @NotBlank @Pattern(regexp = BotChatRequest.CHAT_ID) String telegramChatId,
    @NotBlank @Size(max = 200) String title) {}
