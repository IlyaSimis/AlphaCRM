package ru.quietclinic.staff;

import java.time.LocalDate;
import java.util.UUID;

record HrContractView(
    UUID contractId,
    UUID staffId,
    String fullName,
    String position,
    long rateMinor,
    String rateUnit,
    String currency,
    LocalDate validFrom,
    LocalDate validTo) {

  static HrContractView of(HrContract contract, String fullName) {
    return new HrContractView(
        contract.id,
        contract.staffId,
        fullName,
        contract.position,
        contract.rateMinor,
        contract.rateUnit,
        contract.currency,
        contract.validFrom,
        contract.validTo);
  }
}
