package ru.quietclinic.tenancy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record CreateTenantRequest(
    @NotBlank @Size(max = 200) String name,
    @NotBlank @Size(max = 200) String adminFullName,
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9._@+-]{3,320}$") String adminUsername,
    @NotBlank @Size(min = 8, max = 200) String adminPassword) {}
