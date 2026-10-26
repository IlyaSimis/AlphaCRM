package ru.quietclinic.patients;

import java.time.LocalDate;
import java.util.UUID;

/** Карточка пациента в том объёме, который нужен боту: ФИО, дата рождения и основной телефон. */
public record TelegramPatient(UUID id, String fullName, LocalDate birthDate, String phone) {

  static TelegramPatient of(Patient patient) {
    return new TelegramPatient(patient.id, patient.fullName, patient.birthDate, patient.phone());
  }
}
