package ru.quietclinic.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Черновик накладной. idempotencyKey — повтор с тем же ключом возвращает уже созданный документ.
 * codes в строке — отсканированные коды DataMatrix (или SGTIN) упаковок маркируемой позиции.
 */
record DocumentRequest(
    @NotBlank @Pattern(regexp = "RECEIPT|CONSUMPTION|WRITE_OFF|TRANSFER|RETURN_TO_SUPPLIER")
        String type,
    LocalDate documentDate,
    @Size(max = 255) String supplier,
    UUID fromWarehouseId,
    UUID toWarehouseId,
    UUID appointmentId,
    @Size(max = 500) String note,
    @NotBlank @Size(max = 160) String idempotencyKey,
    @NotNull @Size(max = 200) List<@Valid Line> lines) {

  record Line(
      @NotNull UUID itemId,
      @NotNull @DecimalMin(value = "0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
      @Size(max = 40) String batch,
      LocalDate expiresOn,
      @Size(max = 500) List<@NotBlank @Size(max = 300) String> codes) {}
}
