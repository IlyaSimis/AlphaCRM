package ru.quietclinic.patients;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhoneIndexTest {
  private static Patient patient(String name, String phone) {
    Patient patient = new Patient(name, LocalDate.parse("1990-01-01"));
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
    Patient anna = patient("Anna", "+7 916 245-18-72");
    Patient copy = patient("Anna (duplicate)", "89162451872");
    Patient other = patient("Dmitry", "+7 903 782-44-16");
    PhoneIndex index =
        new PhoneIndex(List.of(anna.contacts.get(0), copy.contacts.get(0), other.contacts.get(0)));

    assertThat(index.sharedWith(anna))
        .extracting(PatientRef::fullName)
        .containsExactly("Anna (duplicate)");
    assertThat(index.sharedWith(other)).isEmpty();
  }

  @Test
  void secondPhoneMatchesTheMainPhoneOfAnotherCardBothWays() {
    Patient anna = patient("Anna", "+7 916 245-18-72");
    Patient boris = patient("Boris", "+7 903 000-00-01");
    boris.contacts.add(new Contact(boris, "SECOND_PHONE", "8 (916) 245-18-72"));
    Patient other = patient("Dmitry", "+7 903 782-44-16");
    PhoneIndex index =
        new PhoneIndex(
            List.of(
                anna.contacts.get(0),
                boris.contacts.get(0),
                boris.contacts.get(1),
                other.contacts.get(0)));

    assertThat(index.sharedWith(anna)).extracting(PatientRef::fullName).containsExactly("Boris");
    assertThat(index.sharedWith(boris)).extracting(PatientRef::fullName).containsExactly("Anna");
    assertThat(index.sharedWith(other)).isEmpty();
  }
}
