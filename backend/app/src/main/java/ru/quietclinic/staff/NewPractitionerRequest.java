package ru.quietclinic.staff;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Карточка врача, которая создаётся вместе с учётной записью; ФИО — то же, что у сотрудника. */
record NewPractitionerRequest(
    @NotBlank @Size(max = 120) String specialty,
    @Size(max = 80) String room,
    @NotNull List<@Valid ShiftSpec> shifts) {}
