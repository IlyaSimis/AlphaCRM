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
import ru.quietclinic.platform.PlatformConfig;

/**
 * Пациенты, которые пишут боту клиники в Telegram или MAX. Один внешний id может стоять у
 * нескольких карточек (человек записывает и себя, и родственника), карточкой «по умолчанию»
 * считается самая новая. Вызывающий — сервисная учётная запись бота с ролью BOT. Историческое имя
 * класса сохранено ради совместимости публичной границы модуля.
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
  private final PatientPrivacyService privacy;
  private final ContactTokenService tokens;

  TelegramPatients(
      PatientRepository patients,
      ContactRepository contacts,
      AuditService audit,
      PatientPrivacyService privacy,
      ContactTokenService tokens) {
    this.patients = patients;
    this.contacts = contacts;
    this.audit = audit;
    this.privacy = privacy;
    this.tokens = tokens;
  }

  /** Самая новая карточка клиники с этим внешним id. */
  @Transactional(readOnly = true)
  public Optional<TelegramPatient> findByChatId(String chatId) {
    return findByExternalId(MessengerChannel.TELEGRAM, chatId);
  }

  @Transactional(readOnly = true)
  public Optional<TelegramPatient> findByExternalId(MessengerChannel channel, String externalId) {
    requireRole(ROLE);
    return linked(channel, externalId).stream().findFirst().map(TelegramPatient::of);
  }

  @Transactional(readOnly = true)
  public List<TelegramPatient> findAllByExternalId(MessengerChannel channel, String externalId) {
    requireRole(ROLE);
    return linked(channel, externalId).stream().map(TelegramPatient::of).toList();
  }

  @Transactional(readOnly = true)
  public Optional<TelegramPatient> find(UUID patientId) {
    requireRole(ROLE);
    return patients
        .findByIdAndClinicId(patientId, clinicId())
        .map(
            patient -> {
              privacy.decrypt(patient);
              return TelegramPatient.of(patient);
            });
  }

  /** Только владелец связанного внешнего id может управлять записью пациента. */
  @Transactional(readOnly = true)
  public boolean isLinked(UUID patientId, String chatId) {
    return isLinked(patientId, MessengerChannel.TELEGRAM, chatId);
  }

  @Transactional(readOnly = true)
  public boolean isLinked(UUID patientId, MessengerChannel channel, String externalId) {
    requireRole(ROLE);
    return linked(channel, externalId).stream().anyMatch(p -> p.id.equals(patientId));
  }

  /** Погашение одноразовой ссылки из карточки: существующий пациент, без создания дубля. */
  @Transactional
  public TelegramPatient link(UUID patientId, MessengerChannel channel, String externalId) {
    requireRole(ROLE);
    Patient patient =
        patients
            .findByIdAndClinicId(patientId, clinicId())
            .orElseThrow(() -> PlatformConfig.notFound("PATIENT_NOT_FOUND", "Patient not found"));
    privacy.decrypt(patient);
    patient.updateMessenger(channel, externalId, false);
    if (patient.preferredMessenger == null) patient.preferMessenger(channel);
    privacy.encrypt(patient);
    Patient saved = patients.saveAndFlush(patient);
    // После merge Hibernate может вернуть другой экземпляр: transient-значения нужно восстановить.
    privacy.decrypt(saved);
    tokens.rebuild(saved);
    audit.record(
        actorId(),
        "PATIENT_MESSENGER_LINKED",
        "PATIENT",
        patient.id,
        "ALLOWED",
        channel.name().toLowerCase(Locale.ROOT));
    return TelegramPatient.of(patient);
  }

  @Transactional
  public UUID resolveForBooking(TelegramPatientRequest request) {
    return resolveForBooking(MessengerChannel.TELEGRAM, request);
  }

  /** Общая логика поиска существующей карточки и согласия для обоих мессенджеров. */
  @Transactional
  public UUID resolveForBooking(MessengerChannel channel, TelegramPatientRequest request) {
    requireRole(ROLE);
    String name = request.fullName().trim().replaceAll("\\s+", " ");
    String phoneKey = PhoneIndex.normalize(request.phone());
    List<Patient> alreadyLinked = linked(channel, request.chatId());
    Optional<Patient> existing =
        contacts.findByTypeInAndPatientClinicId(PhoneIndex.PHONE_TYPES, clinicId()).stream()
            .peek(privacy::decrypt)
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
      Patient patient = existing.get();
      if (patient.updateMessenger(channel, request.chatId(), false)) {
        audit.record(
            actorId(),
            "PATIENT_REGISTRY_UPDATED",
            "PATIENT",
            patient.id,
            "ALLOWED",
            channel.name().toLowerCase(Locale.ROOT));
      }
      if (patient.preferredMessenger == null) patient.preferMessenger(channel);
      if (!patient.hasActiveConsent()) {
        Consent consent = patient.grantConsent(request.consentVersion(), channel.consentBasis());
        patients.saveAndFlush(patient);
        audit.record(
            actorId(), "PATIENT_CONSENT_CHANGED", "CONSENT", consent.id, "ALLOWED", "granted");
      }
      privacy.encrypt(patient);
      Patient saved = patients.saveAndFlush(patient);
      privacy.decrypt(saved);
      tokens.rebuild(saved);
      return saved.id;
    }
    Patient patient = new Patient(name, request.birthDate());
    patient.clinicId = clinicId();
    patient.update(
        new PatientDetails(name, request.birthDate(), request.phone(), null, null, null));
    patient.updateMessenger(channel, request.chatId(), false);
    patient.preferMessenger(channel);
    patient.consents.add(
        new Consent(patient, request.consentVersion().trim(), channel.consentBasis()));
    privacy.encrypt(patient);
    Patient saved = patients.saveAndFlush(patient);
    privacy.decrypt(saved);
    tokens.rebuild(saved);
    audit.record(
        actorId(), "PATIENT_CREATED", "PATIENT", saved.id, "ALLOWED", channel.consentBasis());
    return saved.id;
  }

  /** Карточки клиники с этим внешним id, новые сверху. */
  private List<Patient> linked(MessengerChannel channel, String externalId) {
    List<Patient> result =
        patients.findAllById(tokens.channelMatches(clinicId(), channel, externalId));
    result.removeIf(patient -> !clinicId().equals(patient.clinicId));
    result.forEach(privacy::decrypt);
    return result.stream()
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
