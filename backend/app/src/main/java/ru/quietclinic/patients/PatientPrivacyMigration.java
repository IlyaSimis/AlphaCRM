package ru.quietclinic.patients;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.quietclinic.tenancy.TenantExecutor;

/** Одноразово переводит старые JSONB/открытые контакты в шифртекст и перестраивает HMAC-индекс. */
@Component
class PatientPrivacyMigration implements ApplicationRunner {
  private final PatientRepository patients;
  private final PatientPrivacyService privacy;
  private final ContactTokenService tokens;
  private final TenantExecutor tenants;
  private final PlatformTransactionManager transactions;

  PatientPrivacyMigration(
      PatientRepository patients,
      PatientPrivacyService privacy,
      ContactTokenService tokens,
      TenantExecutor tenants,
      PlatformTransactionManager transactions) {
    this.patients = patients;
    this.privacy = privacy;
    this.tokens = tokens;
    this.tenants = tenants;
    this.transactions = transactions;
  }

  @Override
  public void run(ApplicationArguments args) {
    tenants.forEachTenant(
        () ->
            new TransactionTemplate(transactions)
                .executeWithoutResult(
                    ignored -> {
                      List<Patient> existing = patients.findAll();
                      for (Patient patient : existing) {
                        privacy.decrypt(patient);
                        privacy.encryptLegacy(patient);
                        tokens.rebuild(patient);
                      }
                      patients.saveAll(existing);
                    }));
  }
}
