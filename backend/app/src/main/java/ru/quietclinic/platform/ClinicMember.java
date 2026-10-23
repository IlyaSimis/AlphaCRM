package ru.quietclinic.platform;

import java.util.UUID;

/** Сотрудник в сессии знает свою клинику: выборки и проверки ограничены ею. */
public interface ClinicMember {
  UUID clinicId();
}
