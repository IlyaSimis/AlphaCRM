package ru.quietclinic.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Строка со сканера DataMatrix (как с клавиатуры). */
record ScanRequest(@NotBlank @Size(max = 300) String code) {}
