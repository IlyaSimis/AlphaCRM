package ru.quietclinic.staff;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Новые условия работы с даты validFrom: прежние закрываются днём раньше. */
record HrContractRequest(
    @NotBlank @Size(max = 160) String position,
    @NotNull @PositiveOrZero @Max(100_000_000_00L) Long rateMinor,
    @NotBlank @Pattern(regexp = "MONTH|HOUR|VISIT") String rateUnit,
    @Pattern(regexp = "[A-Z]{3}") String currency,
    @NotNull LocalDate validFrom) {}
