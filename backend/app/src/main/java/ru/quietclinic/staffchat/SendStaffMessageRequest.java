package ru.quietclinic.staffchat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Текстовое сообщение; вложения не входят в первый вертикальный срез. */
record SendStaffMessageRequest(@NotBlank @Size(max = 4000) String text) {}
