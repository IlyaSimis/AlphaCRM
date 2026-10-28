package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Услуга клиники из справочника: длительность и цена в копейках. Услугу не удаляют, а выключают:
 * выключенная не предлагается в новой записи, старые записи хранят свой снимок.
 *
 * <p>Режим цены: {@code FIXED} — цена обязательна; {@code OPEN} (свободная цена) — цены нет, есть
 * пределы {@code 0 ≤ min ≤ max}, сумму вводят при выставлении счёта или оплате.
 */
@Entity
@Table(name = "clinic_service")
class ClinicService {
  static final String FIXED = "FIXED";
  static final String OPEN = "OPEN";

  @Id UUID id;

  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  String name;

  @Column(nullable = false)
  int durationMinutes;

  @Column(nullable = false)
  String priceMode;

  Long priceMinor;

  Long minMinor;

  Long maxMinor;

  @Column(nullable = false)
  String currency;

  @Column(nullable = false)
  boolean active;

  protected ClinicService() {}

  ClinicService(UUID clinicId, String name, int durationMinutes, long priceMinor, String currency) {
    this(clinicId, name, durationMinutes, currency);
    setPricing(FIXED, priceMinor, null, null);
  }

  ClinicService(UUID clinicId, String name, int durationMinutes, String currency) {
    id = UUID.randomUUID();
    this.clinicId = clinicId;
    this.name = name;
    this.durationMinutes = durationMinutes;
    this.currency = currency;
    active = true;
  }

  boolean open() {
    return OPEN.equals(priceMode);
  }

  /** Инвариант режима цены; при нарушении — 409 INVALID_PRICING. */
  void setPricing(String mode, Long price, Long min, Long max) {
    var next = mode == null || mode.isBlank() ? FIXED : mode;
    if (FIXED.equals(next)) {
      if (price == null || price < 0) {
        throw conflict("INVALID_PRICING", "A fixed price must be set and not negative");
      }
      priceMode = FIXED;
      priceMinor = price;
      minMinor = null;
      maxMinor = null;
    } else if (OPEN.equals(next)) {
      if (min == null || max == null || min < 0 || max < min) {
        throw conflict("INVALID_PRICING", "An open price needs limits 0 <= min <= max");
      }
      priceMode = OPEN;
      priceMinor = null;
      minMinor = min;
      maxMinor = max;
    } else {
      throw conflict("INVALID_PRICING", "Unknown price mode");
    }
  }

  /** Сумма в пределах свободной цены. */
  boolean withinLimits(long amountMinor) {
    return minMinor != null
        && maxMinor != null
        && amountMinor >= minMinor
        && amountMinor <= maxMinor;
  }
}
