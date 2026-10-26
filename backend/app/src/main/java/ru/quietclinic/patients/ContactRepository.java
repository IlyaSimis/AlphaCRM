package ru.quietclinic.patients;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ContactRepository extends JpaRepository<Contact, UUID> {
  /** Контакты карточек одной клиники: совпадение телефона с чужой клиникой не показывается. */
  List<Contact> findByTypeInAndPatientClinicId(Collection<String> types, UUID clinicId);

  /** Контакты одного типа и значения в клинике: например, все карточки с этим chat id Telegram. */
  List<Contact> findByTypeAndValueAndPatientClinicId(String type, String value, UUID clinicId);
}
