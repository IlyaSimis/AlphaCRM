package ru.quietclinic.scheduling;

import jakarta.validation.constraints.NotBlank;

record ChangeStatusRequest(@NotBlank String status) {}
