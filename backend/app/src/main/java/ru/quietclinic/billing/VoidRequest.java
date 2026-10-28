package ru.quietclinic.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Причина аннулирования — код из списка: дубль, ошибка, пациент отказался, другое. */
record VoidRequest(
    @NotBlank @Pattern(regexp = "DUPLICATE|ERROR|PATIENT_DECLINED|OTHER") String reason) {}
