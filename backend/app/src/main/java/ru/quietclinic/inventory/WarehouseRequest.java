package ru.quietclinic.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record WarehouseRequest(@NotBlank @Size(max = 160) String name) {}
