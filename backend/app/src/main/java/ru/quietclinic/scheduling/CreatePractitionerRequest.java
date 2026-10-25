package ru.quietclinic.scheduling;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Новая карточка врача: ФИО, специальность, кабинет (необязателен) и недельный график. */
record CreatePractitionerRequest(
    @NotBlank @Size(max = 255) String fullName,
    @NotBlank @Size(max = 120) String specialty,
    @Size(max = 80) String room,
    @NotNull List<@Valid ShiftRequest> shifts) {}
