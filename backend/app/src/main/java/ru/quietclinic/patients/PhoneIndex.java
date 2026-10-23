package ru.quietclinic.patients;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Поиск карточек с тем же телефоном. Номера сравниваются по цифрам: «+7 916 …» = «7916…». */
final class PhoneIndex {
  private final Map<String, List<Patient>> byPhone = new HashMap<>();

  PhoneIndex(List<Contact> phones) {
    for (var contact : phones) {
      var key = normalize(contact.value);
      if (!key.isEmpty()) byPhone.computeIfAbsent(key, k -> new ArrayList<>()).add(contact.patient);
    }
  }

  static String normalize(String phone) {
    if (phone == null) return "";
    var digits = phone.replaceAll("\\D", "");
    // 8 916… и +7 916… — один номер.
    if (digits.length() == 11 && digits.startsWith("8")) digits = "7" + digits.substring(1);
    return digits;
  }

  List<PatientRef> sharedWith(Patient patient) {
    var phone = patient.phone();
    if (phone == null) return List.of();
    UUID self = patient.id;
    return byPhone.getOrDefault(normalize(phone), List.of()).stream()
        .filter(other -> !other.id.equals(self))
        .map(other -> new PatientRef(other.id, other.fullName))
        .distinct()
        .toList();
  }
}
