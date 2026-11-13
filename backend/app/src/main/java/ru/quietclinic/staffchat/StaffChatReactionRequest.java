package ru.quietclinic.staffchat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record StaffChatReactionRequest(@NotBlank @Size(max = 16) String emoji) {}
