package ru.quietclinic.patients;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ContactRepository extends JpaRepository<Contact, UUID> {
  /** Контакты карточек одной клиники: совпадение телефона с чужой клиникой не показывается. */
  List<Contact> findByTypeInAndPatientClinicId(Collection<String> types, UUID clinicId);

  List<Contact> findByPatientIdAndType(UUID patientId, String type);
}
