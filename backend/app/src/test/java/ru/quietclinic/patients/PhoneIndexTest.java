package ru.quietclinic.patients;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhoneIndexTest {
  private static Patient patient(String name, String phone) {
    var patient = new Patient(name, LocalDate.parse("1990-01-01"));
    patient.contacts.add(new Contact(patient, "PHONE", phone));
    return patient;
  }

  @Test
  void comparesPhonesByDigitsAndTreatsLeadingEightAsSeven() {
    assertThat(PhoneIndex.normalize("+7 (916) 245-18-72")).isEqualTo("79162451872");
    assertThat(PhoneIndex.normalize("8 916 245 18 72")).isEqualTo("79162451872");
    assertThat(PhoneIndex.normalize(null)).isEmpty();
  }

  @Test
  void findsOtherCardsWithTheSamePhone() {
    var anna = patient("Anna", "+7 916 245-18-72");
    var copy = patient("Anna (duplicate)", "89162451872");
    var other = patient("Dmitry", "+7 903 782-44-16");
    var index =
        new PhoneIndex(List.of(anna.contacts.get(0), copy.contacts.get(0), other.contacts.get(0)));

    assertThat(index.sharedWith(anna))
        .extracting(PatientRef::fullName)
        .containsExactly("Anna (duplicate)");
    assertThat(index.sharedWith(other)).isEmpty();
  }
}
