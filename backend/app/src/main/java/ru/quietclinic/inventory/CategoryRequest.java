package ru.quietclinic.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record CategoryRequest(
    @NotBlank @Size(max = 160) String name,
    UUID parentId,
    @NotBlank @Pattern(regexp = "DRUG|MEDICAL_DEVICE|CONSUMABLE|GOODS") String kind) {}
