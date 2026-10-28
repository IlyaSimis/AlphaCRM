package ru.quietclinic.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Условия работы сотрудника с даты validFrom; validTo пусто — действуют сейчас. */
@Entity
@Table(name = "hr_contract")
class HrContract {
  @Id UUID id;

  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  UUID staffId;

  @Column(nullable = false)
  String position;

  @Column(nullable = false)
  long rateMinor;

  /** MONTH — оклад в месяц, HOUR — за час, VISIT — за приём. */
  @Column(nullable = false)
  String rateUnit;

  @Column(nullable = false)
  String currency;

  @Column(nullable = false)
  LocalDate validFrom;

  LocalDate validTo;

  @Column(nullable = false)
  UUID createdBy;

  @Column(nullable = false)
  Instant createdAt;

  protected HrContract() {}

  HrContract(
      UUID clinicId,
      UUID staffId,
      String position,
      long rateMinor,
      String rateUnit,
      String currency,
      LocalDate validFrom,
      UUID createdBy) {
    id = UUID.randomUUID();
    this.clinicId = clinicId;
    this.staffId = staffId;
    this.position = position;
    this.rateMinor = rateMinor;
    this.rateUnit = rateUnit;
    this.currency = currency;
    this.validFrom = validFrom;
    this.createdBy = createdBy;
    createdAt = Instant.now();
  }
}
