package ru.quietclinic.platform;

import java.util.UUID;

/** Сотрудник в сессии знает свою клинику: выборки и проверки ограничены ею. */
public interface ClinicMember {
  UUID clinicId();

  /**
   * Id учётной записи сотрудника (staff_user.id). У врача он может отличаться от автора действий
   * ({@link PlatformConfig#actorId()} — id карточки врача). Пусто у сервисных учётных записей.
   */
  default UUID staffId() {
    return null;
  }
}
