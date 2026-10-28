package ru.quietclinic.staff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Все роли сотрудника после изменения: хотя бы одна. */
record StaffRolesRequest(@NotEmpty @Size(max = 8) List<@NotBlank String> roles) {}
