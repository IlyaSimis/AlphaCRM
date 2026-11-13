package ru.quietclinic.scheduling;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Нормы расхода услуги целиком: старые заменяются новыми. */
record ServiceNormsRequest(@NotNull @Size(max = 50) List<@Valid Norm> norms) {
  record Norm(
      @NotNull UUID itemId,
      @NotNull @DecimalMin(value = "0.001") @Digits(integer = 11, fraction = 3)
          BigDecimal quantity) {}
}
