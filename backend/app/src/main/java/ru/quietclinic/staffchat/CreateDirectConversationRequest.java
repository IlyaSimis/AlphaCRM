package ru.quietclinic.staffchat;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Личная беседа с другим активным сотрудником той же клиники. */
record CreateDirectConversationRequest(@NotNull UUID staffId) {}
