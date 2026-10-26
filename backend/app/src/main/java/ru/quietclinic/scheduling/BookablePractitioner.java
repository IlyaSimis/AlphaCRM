package ru.quietclinic.scheduling;

import java.util.UUID;

/** Врач, к которому можно записаться онлайн: есть хотя бы одна смена. room — кабинет или null. */
public record BookablePractitioner(UUID id, String fullName, String specialty, String room) {

  static BookablePractitioner of(Practitioner p) {
    return new BookablePractitioner(p.id, p.fullName, p.specialty, p.room);
  }
}
