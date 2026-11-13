package ru.quietclinic.staffchat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Группа минимум из трёх человек: создатель и не менее двух выбранных сотрудников. */
record CreateGroupConversationRequest(
    @NotBlank @Size(max = 100) String title,
    @NotNull @Size(min = 2, max = 99) List<UUID> memberIds) {}
