package ru.quietclinic.communications;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Постановка в outbox. Модули зовут его по событию — «запись создана», «запись отменена», «оплата
 * принята», — а что именно и когда отправить, решает этот модуль. В payload только машинные поля;
 * текст сообщения собирается из записи при доставке.
 */
@Service
public class OutboxService {
  static final Duration REMINDER_LEAD = Duration.ofHours(24);
  /** Причина SKIPPED у подтверждения записи, сделанной в боте: пациент уже видел ответ бота. */
  static final String SHOWN_IN_BOT = "SHOWN_IN_BOT";

  private final OutboxRepository repository;
  private final JdbcTemplate jdbc;
  private final ZoneId zone;

  OutboxService(
      OutboxRepository repository,
      JdbcTemplate jdbc,
      @Value("${clinic.time-zone:UTC}") String zone) {
    this.repository = repository;
    this.jdbc = jdbc;
    this.zone = ZoneId.of(zone);
  }

  /** Подтверждение сразу; напоминание за сутки — если запись на будущий день. */
  @Transactional
  public void appointmentBooked(UUID appointmentId, Instant startsAt) {
    scheduleFor(appointmentId, startsAt, clinicOfAppointment(appointmentId), Instant.now());
  }

  /**
   * Запись из Telegram-бота: подтверждение пациент уже увидел в ответе бота, поэтому строка
   * подтверждения сразу закрывается как SKIPPED с причиной SHOWN_IN_BOT, а напоминание за сутки
   * ставится как обычно. Строка подтверждения нужна доставщику: по ней он замечает перенос записи.
   */
  @Transactional
  public void appointmentBookedOnline(UUID appointmentId, Instant startsAt) {
    var now = Instant.now();
    scheduleFor(appointmentId, startsAt, clinicOfAppointment(appointmentId), now);
    repository
        .findByIdempotencyKey(key(NoticeTemplates.CONFIRMED, appointmentId, startsAt))
        .filter(message -> "PENDING".equals(message.status))
        .ifPresent(
            message -> {
              message.status = "SKIPPED";
              message.lastError = SHOWN_IN_BOT;
              message.lastAttemptAt = now;
              message.nextAttemptAt = null;
            });
  }

  /** Сообщение об отмене. Неотправленные подтверждение и напоминание отменит доставщик. */
  @Transactional
  public void appointmentCancelled(UUID appointmentId, Instant startsAt) {
    enqueue(
        NoticeTemplates.NOTIFICATION,
        "APPOINTMENT",
        appointmentId,
        clinicOfAppointment(appointmentId),
        NoticeTemplates.CANCELLED,
        NoticeTemplates.CANCELLED + ":" + appointmentId,
        startsAt,
        Instant.now());
  }

  /**
   * Чек по оплате уходит через порт кассы; пока касса не подключена, чек остаётся QUEUED. Клиника —
   * по записи, за которую платят: строка платежа к этому моменту может быть ещё не записана.
   */
  @Transactional
  public void fiscalReceipt(UUID paymentId, UUID appointmentId) {
    enqueue(
        NoticeTemplates.FISCALIZATION,
        "PAYMENT",
        paymentId,
        clinicOfAppointment(appointmentId),
        NoticeTemplates.RECEIPT,
        NoticeTemplates.RECEIPT + ":" + paymentId,
        null,
        Instant.now());
  }

  /** Подтверждение и напоминание на конкретное время записи — при создании и после переноса. */
  void scheduleFor(UUID appointmentId, Instant startsAt, UUID clinicId, Instant now) {
    enqueue(
        NoticeTemplates.NOTIFICATION,
        "APPOINTMENT",
        appointmentId,
        clinicId,
        NoticeTemplates.CONFIRMED,
        key(NoticeTemplates.CONFIRMED, appointmentId, startsAt),
        startsAt,
        now);
    var today = now.atZone(zone).toLocalDate();
    if (startsAt.atZone(zone).toLocalDate().isAfter(today)) {
      var due = startsAt.minus(REMINDER_LEAD);
      enqueue(
          NoticeTemplates.NOTIFICATION,
          "APPOINTMENT",
          appointmentId,
          clinicId,
          NoticeTemplates.REMINDER,
          key(NoticeTemplates.REMINDER, appointmentId, startsAt),
          startsAt,
          due.isAfter(now) ? due : now);
    }
  }

  private void enqueue(
      String topic,
      String aggregateType,
      UUID aggregateId,
      UUID clinicId,
      String template,
      String idempotencyKey,
      Instant appointmentStartsAt,
      Instant notBefore) {
    // Ключ уникален: повторное событие не ставит второе такое же сообщение.
    if (repository.existsByIdempotencyKey(idempotencyKey)) return;
    repository.save(
        new OutboxMessage(
            topic,
            aggregateType,
            aggregateId,
            clinicId,
            template,
            idempotencyKey,
            appointmentStartsAt,
            Payloads.of(template, aggregateType, aggregateId, appointmentStartsAt),
            notBefore));
  }

  private UUID clinicOfAppointment(UUID appointmentId) {
    return jdbc.queryForObject(
        "select p.clinic_id from appointment a join practitioner p on p.id = a.practitioner_id"
            + " where a.id = ?",
        UUID.class,
        appointmentId);
  }

  static String key(String template, UUID appointmentId, Instant startsAt) {
    return template + ":" + appointmentId + ":" + startsAt.getEpochSecond();
  }
}
