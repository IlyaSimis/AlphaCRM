package ru.quietclinic.staffchat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Последнее сообщение, которое сотрудник действительно увидел в открытой беседе. */
record MarkConversationReadRequest(@NotNull @Positive Long messageId) {}
