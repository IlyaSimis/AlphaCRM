package ru.quietclinic.clinical;

import jakarta.validation.constraints.NotBlank;

/**
 * Черновик документа. Если canonicalContent не передан, документ собирается из текущей заметки
 * приёма.
 */
record DocumentRequest(@NotBlank String templateVersion, String canonicalContent) {}
