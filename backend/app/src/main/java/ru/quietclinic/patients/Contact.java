package ru.quietclinic.patients;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "patient_contact")
class Contact {
  @Id UUID id;

  @jakarta.persistence.ManyToOne(optional = false)
  Patient patient;

  @Column(nullable = false)
  String type;

  @Column(name = "contact_value", nullable = false)
  String value;

  protected Contact() {}

  Contact(Patient patient, String type, String value) {
    id = UUID.randomUUID();
    this.patient = patient;
    this.type = type;
    this.value = value;
  }
}
