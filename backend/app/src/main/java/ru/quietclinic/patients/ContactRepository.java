package ru.quietclinic.patients;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ContactRepository extends JpaRepository<Contact, UUID> {
  /** Контакты карточек одной клиники: совпадение телефона с чужой клиникой не показывается. */
  List<Contact> findByTypeAndPatientClinicId(String type, UUID clinicId);
}
