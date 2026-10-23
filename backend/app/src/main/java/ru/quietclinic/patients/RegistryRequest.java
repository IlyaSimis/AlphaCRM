package ru.quietclinic.patients;

import jakarta.validation.constraints.Size;

/** Административные поля регистратуры. Пустое значение очищает поле. */
record RegistryRequest(String referralSource, @Size(max = 1000) String registryNote) {}
