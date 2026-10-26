package ru.quietclinic.patients;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;

/**
 * Пациенты, которые пишут в Telegram-бот клиники. Связь — контакт TELEGRAM с chat id: один chat id
 * может стоять у нескольких карточек (человек записывает и себя, и родственника), карточкой «по
 * умолчанию» считается самая новая. Вызывающий — сервисная учётная запись бота с ролью BOT.
 */
@Service
public class TelegramPatients {
  static final String ROLE = "BOT";

  /** Основание согласия, данного в боте; версия приходит из бота. */
  public static final String CONSENT_BASIS = "telegram-bot";

  /** Основание в аудите: действие пришло из Telegram-бота. */
  static final String REASON = "telegram-bot";

  private final PatientRepository patients;
  private final ContactRepository contacts;
  private final AuditService audit;

  TelegramPatients(PatientRepository patients, ContactRepository contacts, AuditService audit) {
    this.patients = patients;
    this.contacts = contacts;
    this.audit = audit;
  }

  /** Самая новая карточка клиники с этим chat id. */
  @Transactional(readOnly = true)
  public Optional<TelegramPatient> findByChatId(String chatId) {
    requireRole(ROLE);
    return linked(chatId).stream().findFirst().map(TelegramPatient::of);
  }

  @Transactional(readOnly = true)
  public Optional<TelegramPatient> find(UUID patientId) {
    requireRole(ROLE);
    return patients.findByIdAndClinicId(patientId, clinicId()).map(TelegramPatient::of);
  }

  /** Карточка связана с этим chat id: только такой пациент может подтвердить или отменить запись. */
  @Transactional(readOnly = true)
  public boolean isLinked(UUID patientId, String chatId) {
    requireRole(ROLE);
    return linked(chatId).stream().anyMatch(p -> p.id.equals(patientId));
  }

  /**
   * Карточка для онлайн-записи. Сначала ищется уже заведённая карточка того же человека — то же
   * ФИО, дата рождения и телефон (основной или второй); среди них — уже связанная с этим chat id. Её
   * связывает с Telegram и, если действующего согласия нет, добавляет согласие из бота. Не нашлась —
   * заводится новая карточка с телефоном, контактом Telegram и согласием.
   */
  @Transactional
  public UUID resolveForBooking(TelegramPatientRequest request) {
    requireRole(ROLE);
    var name = request.fullName().trim().replaceAll("\\s+", " ");
    var phoneKey = PhoneIndex.normalize(request.phone());
    var alreadyLinked = linked(request.chatId());
    var existing =
        contacts.findByTypeInAndPatientClinicId(PhoneIndex.PHONE_TYPES, clinicId()).stream()
            .filter(c -> PhoneIndex.normalize(c.value).equals(phoneKey))
            .map(c -> c.patient)
            .filter(p -> request.birthDate().equals(p.birthDate))
            .filter(p -> sameName(p.fullName, name))
            .distinct()
            .sorted(
                Comparator.comparing((Patient p) -> !alreadyLinked.contains(p))
                    .thenComparing(p -> p.createdAt, Comparator.reverseOrder()))
            .findFirst();
    if (existing.isPresent()) {
      var patient = existing.get();
      if (patient.updateTelegram(request.chatId(), false)) {
        audit.record(
            actorId(), "PATIENT_REGISTRY_UPDATED", "PATIENT", patient.id, "ALLOWED", "telegram");
      }
      if (!patient.hasActiveConsent()) {
        var consent = patient.grantConsent(request.consentVersion(), CONSENT_BASIS);
        patients.saveAndFlush(patient);
        audit.record(
            actorId(), "PATIENT_CONSENT_CHANGED", "CONSENT", consent.id, "ALLOWED", "granted");
      }
      patients.save(patient);
      return patient.id;
    }
    var patient = new Patient(name, request.birthDate());
    patient.clinicId = clinicId();
    patient.update(new PatientDetails(name, request.birthDate(), request.phone(), null, null, null));
    patient.updateTelegram(request.chatId(), false);
    patient.consents.add(new Consent(patient, request.consentVersion().trim(), CONSENT_BASIS));
    patients.save(patient);
    audit.record(actorId(), "PATIENT_CREATED", "PATIENT", patient.id, "ALLOWED", REASON);
    return patient.id;
  }

  /** Карточки клиники с этим chat id, новые сверху. */
  private List<Patient> linked(String chatId) {
    return contacts
        .findByTypeAndValueAndPatientClinicId(Patient.TELEGRAM, chatId.trim(), clinicId())
        .stream()
        .map(c -> c.patient)
        .distinct()
        .sorted(Comparator.comparing((Patient p) -> p.createdAt).reversed())
        .toList();
  }

  /** ФИО сравниваются без учёта регистра, лишних пробелов и разницы «е» и «ё». */
  static boolean sameName(String left, String right) {
    return normalizeName(left).equals(normalizeName(right));
  }

  private static String normalizeName(String value) {
    return value == null
        ? ""
        : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).replace('ё', 'е');
  }
}
