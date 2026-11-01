package ru.quietclinic.patients;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.integrations.MessengerIntegrationClientFactory;
import ru.quietclinic.platform.EncryptionService;
import ru.quietclinic.platform.Permission;

/** Одноразовые ссылки привязки, отключение каналов и предпочтение пациента. */
@Service
public class PatientMessengerLinks {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final JdbcTemplate jdbc;
  private final EncryptionService encryption;
  private final PatientRepository patients;
  private final PatientPrivacyService privacy;
  private final ContactTokenService tokens;
  private final TelegramPatients messengerPatients;
  private final AuditService audit;
  private final MessengerIntegrationClientFactory integrations;
  private final String telegramUrl;
  private final String maxUrl;

  PatientMessengerLinks(
      JdbcTemplate jdbc,
      EncryptionService encryption,
      PatientRepository patients,
      PatientPrivacyService privacy,
      ContactTokenService tokens,
      TelegramPatients messengerPatients,
      AuditService audit,
      MessengerIntegrationClientFactory integrations,
      @Value("${clinic.messenger.telegram-public-url:}") String telegramUrl,
      @Value("${clinic.messenger.max-public-url:}") String maxUrl) {
    this.jdbc = jdbc;
    this.encryption = encryption;
    this.patients = patients;
    this.privacy = privacy;
    this.tokens = tokens;
    this.messengerPatients = messengerPatients;
    this.audit = audit;
    this.integrations = integrations;
    this.telegramUrl = telegramUrl == null ? "" : telegramUrl.trim();
    this.maxUrl = maxUrl == null ? "" : maxUrl.trim();
  }

  private String publicUrl(MessengerChannel channel) {
    String legacy = channel == MessengerChannel.TELEGRAM ? telegramUrl : maxUrl;
    return integrations
        .forClinic(clinicId(), channel.name())
        .map(integration -> integration.settings().publicUrl())
        .orElse(legacy);
  }

  @Transactional
  MessengerLinkView create(UUID patientId, String rawChannel) {
    requirePermission(Permission.PATIENT_WRITE);
    MessengerChannel channel = MessengerChannel.parse(rawChannel);
    find(patientId);
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant now = Instant.now();
    Instant expires = now.plus(10, ChronoUnit.MINUTES);
    jdbc.update(
        "delete from messenger_link_token where patient_id = ? and channel = ? and consumed_at is null",
        patientId,
        channel.name());
    jdbc.update(
        "insert into messenger_link_token(id, patient_id, clinic_id, channel, token_hash, created_at, expires_at)"
            + " values (?, ?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        patientId,
        clinicId(),
        channel.name(),
        hash(token),
        Timestamp.from(now),
        Timestamp.from(expires));
    String base = publicUrl(channel);
    String url =
        base.isBlank() ? null : base + (base.contains("?") ? "&start=" : "?start=") + token;
    audit.record(
        actorId(),
        "PATIENT_MESSENGER_LINK_CREATED",
        "PATIENT",
        patientId,
        "ALLOWED",
        channel.name().toLowerCase(Locale.ROOT));
    return new MessengerLinkView(channel.name(), token, url, expires);
  }

  /** Атомарно погашает токен и привязывает именно ту карточку, для которой его выдали. */
  @Transactional
  public TelegramPatient consume(MessengerChannel channel, String externalId, String token) {
    requireRole(TelegramPatients.ROLE);
    Instant now = Instant.now();
    List<LinkRow> rows =
        jdbc.query(
            "select id, patient_id from messenger_link_token where clinic_id = ? and channel = ?"
                + " and token_hash = ? and consumed_at is null and expires_at > ?",
            (rs, i) ->
                new LinkRow(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class)),
            clinicId(),
            channel.name(),
            hash(token),
            Timestamp.from(now));
    if (rows.isEmpty())
      throw conflict("MESSENGER_LINK_INVALID", "Link is invalid, used or expired");
    LinkRow row = rows.get(0);
    int changed =
        jdbc.update(
            "update messenger_link_token set consumed_at = ? where id = ? and consumed_at is null",
            Timestamp.from(now),
            row.id());
    if (changed != 1) throw conflict("MESSENGER_LINK_INVALID", "Link is invalid, used or expired");
    return messengerPatients.link(row.patientId(), channel, externalId);
  }

  @Transactional
  PatientView disconnect(UUID patientId, String rawChannel) {
    requirePermission(Permission.PATIENT_WRITE);
    MessengerChannel channel = MessengerChannel.parse(rawChannel);
    Patient patient = find(patientId);
    patient.updateMessenger(channel, null, true);
    return save(patient, "PATIENT_MESSENGER_DISCONNECTED", channel);
  }

  @Transactional
  PatientView prefer(UUID patientId, String rawChannel) {
    requirePermission(Permission.PATIENT_WRITE);
    Patient patient = find(patientId);
    MessengerChannel channel =
        rawChannel == null || rawChannel.isBlank() ? null : MessengerChannel.parse(rawChannel);
    patient.preferMessenger(channel);
    return save(patient, "PATIENT_MESSENGER_PREFERRED", channel);
  }

  private PatientView save(Patient patient, String action, MessengerChannel channel) {
    privacy.encrypt(patient);
    patients.saveAndFlush(patient);
    tokens.rebuild(patient);
    audit.record(
        actorId(),
        action,
        "PATIENT",
        patient.id,
        "ALLOWED",
        channel == null ? "auto" : channel.name().toLowerCase(Locale.ROOT));
    return PatientView.of(patient, List.of(), null);
  }

  private Patient find(UUID id) {
    Patient patient =
        patients
            .findByIdAndClinicId(id, clinicId())
            .orElseThrow(() -> notFound("PATIENT_NOT_FOUND", "Patient not found"));
    privacy.decrypt(patient);
    return patient;
  }

  private String hash(String token) {
    return encryption.blindIndex("messenger-link", token.trim());
  }

  private record LinkRow(UUID id, UUID patientId) {}
}
