package ru.quietclinic.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Позиция номенклатуры; для маркируемой — GTIN (14 цифр) и система маркировки. Без categoryId
 * позиция попадает в основную категорию своего вида (kind, по умолчанию расходники).
 */
record ItemRequest(
    UUID categoryId,
    @Pattern(regexp = "DRUG|MEDICAL_DEVICE|CONSUMABLE|GOODS") String kind,
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 20) String unit,
    @Pattern(regexp = "\\d{14}") String gtin,
    Boolean marked,
    @Pattern(regexp = "NONE|MDLP|GIS_MT") String markingSystem,
    Boolean rxOnly,
    Boolean active) {}
