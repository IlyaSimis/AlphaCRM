package ru.quietclinic;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {
  @Test
  void moduleBoundariesAreValid() {
    ApplicationModules.of(MedicalApplication.class).verify();
  }
}
