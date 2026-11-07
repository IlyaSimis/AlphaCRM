package ru.quietclinic.staffchat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Текстовое сообщение и явно выбранные в интерфейсе упоминания участников. */
record SendStaffMessageRequest(@NotBlank @Size(max = 4000) String text, List<UUID> mentionIds) {}
