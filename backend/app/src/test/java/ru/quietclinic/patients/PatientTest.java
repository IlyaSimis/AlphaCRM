package ru.quietclinic.patients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PatientTest {
  private static Patient patientWithPhone(String phone) {
    var patient = new Patient("Anna Mikhailova", LocalDate.parse("1991-05-14"));
    patient.contacts.add(new Contact(patient, "PHONE", phone));
    return patient;
  }

  @Test
  void reportsOnlyChangedFieldsAndTrimsInput() {
    var patient = patientWithPhone("+79160000000");

    var changed =
        patient.update("  Anna Mikhailova-Orlova ", LocalDate.parse("1991-05-14"), "+79160000000");

    assertThat(changed).containsExactly("fullName");
    assertThat(patient.fullName).isEqualTo("Anna Mikhailova-Orlova");
  }

  @Test
  void updatesExistingPhoneInPlace() {
    var patient = patientWithPhone("+79160000000");

    var changed = patient.update("Anna Mikhailova", LocalDate.parse("1991-05-15"), "+79990000000");

    assertThat(changed).containsExactly("birthDate", "phone");
    assertThat(patient.contacts).hasSize(1);
    assertThat(patient.contacts.get(0).value).isEqualTo("+79990000000");
  }

  @Test
  void addsPhoneWhenPatientHadNone() {
    var patient = new Patient("Dmitry Volkov", LocalDate.parse("1985-11-02"));

    var changed = patient.update("Dmitry Volkov", LocalDate.parse("1985-11-02"), "+79030000000");

    assertThat(changed).containsExactly("phone");
    assertThat(patient.contacts).extracting(c -> c.type).containsExactly("PHONE");
  }

  @Test
  void noChangesGivesEmptyList() {
    var patient = patientWithPhone("+79160000000");

    assertThat(patient.update("Anna Mikhailova", LocalDate.parse("1991-05-14"), "+79160000000"))
        .isEmpty();
  }

  @Test
  void grantsNewConsentAndRevokesOnlyOnce() {
    var patient = patientWithPhone("+79160000000");
    assertThat(patient.hasActiveConsent()).isFalse();

    var consent = patient.grantConsent(" v1.3 ", "SIGNED_FORM");
    assertThat(patient.hasActiveConsent()).isTrue();
    assertThat(consent.version).isEqualTo("v1.3");

    patient.revokeConsent(consent.id);
    assertThat(patient.hasActiveConsent()).isFalse();
    assertThat(patient.consents).hasSize(1);
    assertThatThrownBy(() -> patient.revokeConsent(consent.id))
        .hasMessageContaining("already revoked");
    assertThatThrownBy(() -> patient.revokeConsent(java.util.UUID.randomUUID()))
        .hasMessageContaining("not found");
  }

  @Test
  void registryUpdateReportsChangedFieldsAndClearsBlankValues() {
    var patient = patientWithPhone("+79160000000");

    assertThat(patient.updateRegistry("WEBSITE", " Call in the evening "))
        .containsExactly("referralSource", "registryNote");
    assertThat(patient.registryNote).isEqualTo("Call in the evening");
    assertThat(patient.updateRegistry("WEBSITE", "Call in the evening")).isEmpty();
    assertThat(patient.updateRegistry("WEBSITE", "  ")).containsExactly("registryNote");
    assertThat(patient.registryNote).isNull();
  }
}
