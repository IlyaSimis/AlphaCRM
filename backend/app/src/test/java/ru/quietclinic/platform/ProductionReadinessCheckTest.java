package ru.quietclinic.platform;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductionReadinessCheckTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "short-key",
        "local-dev-key-change-me-but-long-enough-0000",
        "local-only-demo-key-change-before-sharing"
      })
  void rejectsMissingShortOrDefaultKeys(String key) {
    assertThatThrownBy(() -> new ProductionReadinessCheck(key))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CLINIC_DEV_KMS_KEY");
  }

  @Test
  void acceptsRandomKey() {
    assertThatCode(
            () ->
                new ProductionReadinessCheck(
                    "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"))
        .doesNotThrowAnyException();
  }
}
