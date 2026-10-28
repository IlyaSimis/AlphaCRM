package ru.quietclinic.scheduling;

import java.math.BigDecimal;
import java.util.UUID;

record ServiceNormView(UUID itemId, BigDecimal quantity) {
  static ServiceNormView of(ServiceNorm norm) {
    return new ServiceNormView(norm.itemId, norm.quantity);
  }
}
