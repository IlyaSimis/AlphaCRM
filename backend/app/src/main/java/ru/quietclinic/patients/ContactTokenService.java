package ru.quietclinic.patients;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.quietclinic.platform.EncryptionService;

/** HMAC-индексы телефонных префиксов и точных служебных контактов без открытых значений. */
@Service
class ContactTokenService {
  private final JdbcTemplate jdbc;
  private final EncryptionService encryption;

  ContactTokenService(JdbcTemplate jdbc, EncryptionService encryption) {
    this.jdbc = jdbc;
    this.encryption = encryption;
  }

  void rebuild(Patient patient) {
    jdbc.update("delete from patient_contact_token where patient_id = ?", patient.id);
    var rows = new ArrayList<Object[]>();
    for (var contact : patient.contacts) {
      if (contact.value == null) continue;
      if (PhoneIndex.PHONE_TYPES.contains(contact.type)) {
        var phone = PhoneIndex.normalize(contact.value);
        for (var length = 1; length <= phone.length(); length++) {
          rows.add(
              new Object[] {
                contact.id,
                patient.id,
                patient.clinicId,
                "PHONE",
                encryption.blindIndex("phone-prefix", phone.substring(0, length))
              });
        }
        if (!phone.isEmpty()) {
          rows.add(
              new Object[] {
                contact.id,
                patient.id,
                patient.clinicId,
                "PHONE_EXACT",
                encryption.blindIndex("phone-exact", phone)
              });
        }
      } else if (Patient.TELEGRAM.equals(contact.type) || Patient.MAX.equals(contact.type)) {
        rows.add(
            new Object[] {
              contact.id,
              patient.id,
              patient.clinicId,
              contact.type,
              encryption.blindIndex(
                  Patient.TELEGRAM.equals(contact.type) ? "telegram-exact" : "max-exact",
                  contact.value.trim())
            });
      }
    }
    jdbc.batchUpdate(
        "insert into patient_contact_token(contact_id, patient_id, clinic_id, kind, token)"
            + " values (?, ?, ?, ?, ?)",
        rows);
  }

  List<UUID> phoneMatches(UUID clinicId, String query) {
    var phone = PhoneIndex.normalize(query);
    if (phone.isEmpty()) return List.of();
    return jdbc.queryForList(
        "select distinct patient_id from patient_contact_token"
            + " where clinic_id = ? and kind = 'PHONE' and token = ?",
        UUID.class,
        clinicId,
        encryption.blindIndex("phone-prefix", phone));
  }

  List<UUID> exactPhoneMatches(UUID clinicId, String query) {
    var phone = PhoneIndex.normalize(query);
    if (phone.isEmpty()) return List.of();
    return jdbc.queryForList(
        "select distinct patient_id from patient_contact_token"
            + " where clinic_id = ? and kind = 'PHONE_EXACT' and token = ?",
        UUID.class,
        clinicId,
        encryption.blindIndex("phone-exact", phone));
  }

  List<UUID> telegramMatches(UUID clinicId, String chatId) {
    return channelMatches(clinicId, MessengerChannel.TELEGRAM, chatId);
  }

  List<UUID> channelMatches(UUID clinicId, MessengerChannel channel, String externalId) {
    return jdbc.queryForList(
        "select distinct patient_id from patient_contact_token"
            + " where clinic_id = ? and kind = ? and token = ?",
        UUID.class,
        clinicId,
        channel.name(),
        encryption.blindIndex(
            channel == MessengerChannel.TELEGRAM ? "telegram-exact" : "max-exact",
            externalId.trim()));
  }
}
