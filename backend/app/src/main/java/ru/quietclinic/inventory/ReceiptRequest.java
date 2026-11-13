package ru.quietclinic.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Быстрый приход: строки с существующей позицией (itemId) или с названием новой — её заведёт склад.
 * Без склада — основной. idempotencyKey — повтор отправки не создаёт второй приход.
 */
record ReceiptRequest(
    UUID warehouseId,
    @Size(max = 255) String supplier,
    LocalDate documentDate,
    @Size(max = 500) String note,
    @NotBlank @Size(max = 160) String idempotencyKey,
    @NotEmpty @Size(max = 500) List<@Valid Line> lines) {

  record Line(
      UUID itemId,
      @Size(max = 255) String name,
      @Pattern(regexp = "DRUG|MEDICAL_DEVICE|CONSUMABLE|GOODS") String kind,
      @Size(max = 20) String unit,
      @Pattern(regexp = "\\d{14}") String gtin,
      @NotNull @DecimalMin(value = "0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
      @Size(max = 40) String batch,
      LocalDate expiresOn,
      @Size(max = 500) List<@NotBlank @Size(max = 300) String> codes) {}
}
