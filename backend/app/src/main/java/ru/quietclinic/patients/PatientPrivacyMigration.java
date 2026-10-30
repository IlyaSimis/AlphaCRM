package ru.quietclinic.patients;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Одноразово переводит старые JSONB/открытые контакты в шифртекст и перестраивает HMAC-индекс. */
@Component
class PatientPrivacyMigration implements ApplicationRunner {
  private final PatientRepository patients;
  private final PatientPrivacyService privacy;
  private final ContactTokenService tokens;

  PatientPrivacyMigration(
      PatientRepository patients, PatientPrivacyService privacy, ContactTokenService tokens) {
    this.patients = patients;
    this.privacy = privacy;
    this.tokens = tokens;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    var existing = patients.findAll();
    for (var patient : existing) {
      privacy.decrypt(patient);
      privacy.encryptLegacy(patient);
      tokens.rebuild(patient);
    }
    patients.saveAll(existing);
  }
}
