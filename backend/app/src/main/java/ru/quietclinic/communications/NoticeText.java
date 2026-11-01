package ru.quietclinic.communications;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Текст сообщения пациенту по шаблону. Сами тексты — в {@code patient-notices.properties}: это
 * текст для человека, в Java его нет. Подставляются только клиника, услуга, врач, дата и время.
 */
@Component
class NoticeText {
  static final String RESOURCE = "patient-notices.properties";
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  private final Properties templates = new Properties();
  private final ZoneId zone;

  NoticeText(@Value("${clinic.time-zone:UTC}") String zone) {
    this.zone = ZoneId.of(zone);
    try (var reader =
        new InputStreamReader(
            new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8)) {
      templates.load(reader);
    } catch (IOException ex) {
      throw new IllegalStateException("Cannot read " + RESOURCE, ex);
    }
  }

  /** Надпись для пациента, не шаблон сообщения: например, кнопка под напоминанием. */
  String label(String key) {
    var value = templates.getProperty(key);
    if (value == null) throw new IllegalStateException("Unknown notice label " + key);
    return value;
  }

  PatientMessage render(String template, String idempotencyKey, AppointmentNotice notice) {
    var pattern = templates.getProperty(template);
    if (pattern == null) throw new IllegalStateException("Unknown notice template " + template);
    var local = notice.startsAt().atZone(zone);
    var date = DATE.format(local);
    var time = TIME.format(local);
    var text =
        pattern
            .replace("{clinic}", notice.clinicName())
            .replace("{service}", notice.serviceName())
            .replace("{practitioner}", notice.practitionerName())
            .replace("{date}", date)
            .replace("{time}", time);
    return new PatientMessage(
        idempotencyKey,
        template,
        notice.appointmentId(),
        notice.patientId(),
        notice.clinicName(),
        notice.serviceName(),
        notice.practitionerName(),
        notice.startsAt(),
        local.toLocalDate().toString(),
        time,
        text,
        notice.telegramChatId(),
        notice.maxUserId(),
        notice.preferredMessenger(),
        notice.clinicId());
  }
}
