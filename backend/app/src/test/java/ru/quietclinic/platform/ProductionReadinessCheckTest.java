package ru.quietclinic.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductionReadinessCheckTest {
  static final String KEY = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
  static final String ADMIN_PASSWORD = "long-enough-admin-1";

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "short-key",
        "local-dev-key-change-me-but-long-enough-0000",
        "local-only-demo-key-change-before-sharing"
      })
  void rejectsMissingShortOrDefaultKeys(String key) {
    assertThatThrownBy(() -> new ProductionReadinessCheck(key, ADMIN_PASSWORD, "", "", "", "", "", "", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CLINIC_DEV_KMS_KEY");
  }

  @Test
  void acceptsRandomKeyAndStrongAdminPassword() {
    assertThatCode(() -> new ProductionReadinessCheck(KEY, ADMIN_PASSWORD, "", "", "", "", "", "", ""))
        .doesNotThrowAnyException();
  }

  /** В prod пароль начального администратора обязателен, не короче 12 символов и не admin. */
  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "admin", "ADMIN", "short-pass1"})
  void rejectsMissingShortOrDefaultAdminPassword(String password) {
    assertThatThrownBy(() -> new ProductionReadinessCheck(KEY, password, "", "", "", "", "", "", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CLINIC_ADMIN_PASSWORD");
  }

  @Test
  void explainsWhyAdminPasswordIsRejected() {
    assertThat(ProductionReadinessCheck.adminPasswordProblem(null))
        .contains("Set CLINIC_ADMIN_PASSWORD");
    assertThat(ProductionReadinessCheck.adminPasswordProblem("admin")).contains("admin");
    assertThat(ProductionReadinessCheck.adminPasswordProblem("a".repeat(11))).contains("12");
    assertThat(ProductionReadinessCheck.adminPasswordProblem("a".repeat(12))).isNull();
  }

  @Test
  void outboundUrlNeedsLongSigningSecret() {
    assertThat(ProductionReadinessCheck.signingSecretProblem("CLINIC_CASHBOX", "", "")).isNull();
    assertThat(
            ProductionReadinessCheck.signingSecretProblem(
                "CLINIC_OUTBOUND_WEBHOOK", "https://bot.example/hook", "short"))
        .contains("CLINIC_OUTBOUND_WEBHOOK_SECRET");
    assertThat(
            ProductionReadinessCheck.signingSecretProblem(
                "CLINIC_OUTBOUND_WEBHOOK", "https://bot.example/hook", "s".repeat(32)))
        .isNull();
  }

  @Test
  void telegramRelayNeedsLongSigningSecret() {
    assertThatThrownBy(() -> new ProductionReadinessCheck(
            KEY, ADMIN_PASSWORD, "", "", "http://10.77.0.2:8081", "short", "", "", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CLINIC_OUTBOUND_TELEGRAM_RELAY_SECRET");
  }

  @Test
  void botTokenIsOptionalButLongWhenSet() {
    assertThat(ProductionReadinessCheck.botTokenProblem("")).isNull();
    assertThat(ProductionReadinessCheck.botTokenProblem("short")).contains("CLINIC_BOT_API_TOKEN");
    assertThat(ProductionReadinessCheck.botTokenProblem("t".repeat(32))).isNull();
  }
}
