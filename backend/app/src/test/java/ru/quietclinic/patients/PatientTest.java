package ru.quietclinic.patients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PatientTest {
  private static Patient patientWithPhone(String phone) {
    Patient patient = new Patient("Anna Mikhailova", LocalDate.parse("1991-05-14"));
    patient.contacts.add(new Contact(patient, "PHONE", phone));
    return patient;
  }

  private static PatientDetails details(String fullName, LocalDate birthDate, String phone) {
    return new PatientDetails(fullName, birthDate, phone, null, null, null);
  }

  @Test
  void reportsOnlyChangedFieldsAndTrimsInput() {
    Patient patient = patientWithPhone("+79160000000");

    List<String> changed =
        patient.update(
            details("  Anna Mikhailova-Orlova ", LocalDate.parse("1991-05-14"), "+79160000000"));

    assertThat(changed).containsExactly("fullName");
    assertThat(patient.fullName).isEqualTo("Anna Mikhailova-Orlova");
  }

  @Test
  void updatesExistingPhoneInPlace() {
    Patient patient = patientWithPhone("+79160000000");

    List<String> changed =
        patient.update(details("Anna Mikhailova", LocalDate.parse("1991-05-15"), "+79990000000"));

    assertThat(changed).containsExactly("birthDate", "phone");
    assertThat(patient.contacts).hasSize(1);
    assertThat(patient.contacts.get(0).value).isEqualTo("+79990000000");
  }

  @Test
  void addsPhoneWhenPatientHadNone() {
    Patient patient = new Patient("Dmitry Volkov", LocalDate.parse("1985-11-02"));

    List<String> changed =
        patient.update(details("Dmitry Volkov", LocalDate.parse("1985-11-02"), "+79030000000"));

    assertThat(changed).containsExactly("phone");
    assertThat(patient.contacts).extracting(c -> c.type).containsExactly("PHONE");
  }

  @Test
  void noChangesGivesEmptyList() {
    Patient patient = patientWithPhone("+79160000000");

    assertThat(
            patient.update(
                details("Anna Mikhailova", LocalDate.parse("1991-05-14"), "+79160000000")))
        .isEmpty();
  }

  @Test
  void emailAndSecondPhoneAreSeparateContactsAndBlankRemovesThem() {
    Patient patient = patientWithPhone("+79160000000");
    UUID staff = UUID.randomUUID();

    List<String> changed =
        patient.update(
            new PatientDetails(
                "Anna Mikhailova",
                LocalDate.parse("1991-05-14"),
                "+79160000000",
                " anna@example.org ",
                "+7 903 000-00-01",
                staff));

    assertThat(changed).containsExactly("email", "secondPhone", "assignedStaff");
    assertThat(patient.email()).isEqualTo("anna@example.org");
    assertThat(patient.secondPhone()).isEqualTo("+7 903 000-00-01");
    // Второй телефон — отдельный контакт: основной телефон не меняется.
    assertThat(patient.phone()).isEqualTo("+79160000000");
    assertThat(patient.contacts)
        .extracting(c -> c.type)
        .containsExactlyInAnyOrder("PHONE", "EMAIL", "SECOND_PHONE");

    List<String> cleared =
        patient.update(
            new PatientDetails(
                "Anna Mikhailova", LocalDate.parse("1991-05-14"), "+79160000000", " ", "", null));

    assertThat(cleared).containsExactly("email", "secondPhone", "assignedStaff");
    assertThat(patient.contacts).extracting(c -> c.type).containsExactly("PHONE");
    assertThat(patient.contacts).extracting(c -> c.value).doesNotContain("");
    assertThat(patient.assignedStaffId).isNull();
  }

  @Test
  void grantsNewConsentAndRevokesOnlyOnce() {
    Patient patient = patientWithPhone("+79160000000");
    assertThat(patient.hasActiveConsent()).isFalse();

    Consent consent = patient.grantConsent(" v1.3 ", "SIGNED_FORM");
    assertThat(patient.hasActiveConsent()).isTrue();
    assertThat(consent.version).isEqualTo("v1.3");

    patient.revokeConsent(consent.id);
    assertThat(patient.hasActiveConsent()).isFalse();
    assertThat(patient.consents).hasSize(1);
    assertThatThrownBy(() -> patient.revokeConsent(consent.id))
        .hasMessageContaining("already revoked");
    assertThatThrownBy(() -> patient.revokeConsent(UUID.randomUUID()))
        .hasMessageContaining("not found");
  }

  @Test
  void registryUpdateReportsChangedFieldsAndClearsBlankValues() {
    Patient patient = patientWithPhone("+79160000000");

    assertThat(patient.updateRegistry("WEBSITE", " Call in the evening "))
        .containsExactly("referralSource", "registryNote");
    assertThat(patient.registryNote).isEqualTo("Call in the evening");
    assertThat(patient.updateRegistry("WEBSITE", "Call in the evening")).isEmpty();
    assertThat(patient.updateRegistry("WEBSITE", "  ")).containsExactly("registryNote");
    assertThat(patient.registryNote).isNull();
  }
}
