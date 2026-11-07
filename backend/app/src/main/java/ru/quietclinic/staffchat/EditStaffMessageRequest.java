package ru.quietclinic.staffchat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record EditStaffMessageRequest(@NotBlank @Size(max = 4000) String text) {}
