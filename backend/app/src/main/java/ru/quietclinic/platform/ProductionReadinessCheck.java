package ru.quietclinic.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Не даёт запустить prod-профиль с ключом шифрования по умолчанию или слишком коротким ключом. */
@Component
@Profile("prod")
class ProductionReadinessCheck {
  static final int MIN_KEY_LENGTH = 32;
  private static final Logger log = LoggerFactory.getLogger(ProductionReadinessCheck.class);

  ProductionReadinessCheck(@Value("${clinic.dev-kms-key:}") String kmsKey) {
    if (kmsKey.isBlank()
        || kmsKey.length() < MIN_KEY_LENGTH
        || kmsKey.contains("change-me")
        || kmsKey.contains("demo-key")) {
      throw new IllegalStateException(
          "CLINIC_DEV_KMS_KEY должен быть случайной строкой не короче "
              + MIN_KEY_LENGTH
              + " символов (см. scripts/init-env.sh)");
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  void warnAboutDemoAuth() {
    log.warn(
        "Аутентификация работает через демо-заголовки X-Demo-Role/X-Actor-Id. "
            + "Не открывайте приложение в сеть, пока не подключён настоящий IAM (OIDC).");
  }
}
