package ru.quietclinic.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Не даёт запустить prod-профиль с ключом шифрования по умолчанию или слишком коротким, а также без
 * надёжного пароля начального администратора. Проверка идёт при создании бина — до того, как
 * приложение заведёт администратора на пустой базе.
 */
@Component
@Profile("prod")
class ProductionReadinessCheck {
  static final int MIN_KEY_LENGTH = 32;
  static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

  ProductionReadinessCheck(
      @Value("${clinic.dev-kms-key:}") String kmsKey,
      @Value("${clinic.admin-password:}") String adminPassword,
      @Value("${clinic.outbound.webhook-url:}") String webhookUrl,
      @Value("${clinic.outbound.webhook-secret:}") String webhookSecret,
      @Value("${clinic.cashbox.url:}") String cashboxUrl,
      @Value("${clinic.cashbox.secret:}") String cashboxSecret) {
    if (kmsKey.isBlank()
        || kmsKey.length() < MIN_KEY_LENGTH
        || kmsKey.contains("change-me")
        || kmsKey.contains("demo-key")) {
      throw new IllegalStateException(
          "CLINIC_DEV_KMS_KEY must be a random string of at least "
              + MIN_KEY_LENGTH
              + " characters (see scripts/init-env.sh)");
    }
    var problem = adminPasswordProblem(adminPassword);
    if (problem != null) throw new IllegalStateException(problem);
    problem = signingSecretProblem("CLINIC_OUTBOUND_WEBHOOK", webhookUrl, webhookSecret);
    if (problem != null) throw new IllegalStateException(problem);
    problem = signingSecretProblem("CLINIC_CASHBOX", cashboxUrl, cashboxSecret);
    if (problem != null) throw new IllegalStateException(problem);
  }

  /** Адрес наружу задан — нужен и секрет подписи HMAC, не короче 32 символов. */
  static String signingSecretProblem(String prefix, String url, String secret) {
    if (url == null || url.isBlank()) return null;
    if (secret == null || secret.length() < MIN_KEY_LENGTH) {
      return prefix
          + "_URL is set: "
          + prefix
          + "_SECRET must be at least "
          + MIN_KEY_LENGTH
          + " characters long";
    }
    return null;
  }

  /** null — пароль годится для prod; иначе — почему приложение не стартует. Пароль не выводится. */
  static String adminPasswordProblem(String password) {
    if (password == null || password.isBlank()) {
      return "Set CLINIC_ADMIN_PASSWORD, the initial administrator password"
          + " (see scripts/init-env.sh)";
    }
    // Сначала «admin»: так причина отказа точнее, чем общее «слишком короткий».
    if (password.equalsIgnoreCase("admin")) {
      return "CLINIC_ADMIN_PASSWORD must not be admin";
    }
    if (password.length() < MIN_ADMIN_PASSWORD_LENGTH) {
      return "CLINIC_ADMIN_PASSWORD must be at least "
          + MIN_ADMIN_PASSWORD_LENGTH
          + " characters long";
    }
    return null;
  }
}
