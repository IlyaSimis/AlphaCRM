package ru.quietclinic.patients;

import java.util.Set;

/** Справочник «откуда пришёл пациент». Подписи — во фронтенде. */
final class ReferralSources {
  static final Set<String> ALL =
      Set.of("RECOMMENDATION", "WEBSITE", "INSURANCE", "DOCTOR_REFERRAL", "OTHER");

  private ReferralSources() {}
}
