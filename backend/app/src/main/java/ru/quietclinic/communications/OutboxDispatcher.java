package ru.quietclinic.communications;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ru.quietclinic.audit.AuditService;

/**
 * Доставщик outbox. Забирает PENDING и FAILED, чьё время пришло, помечает попытку (SENDING),
 * отправляет через порт и ставит SENT; при временной ошибке — FAILED и повтор с паузой. Ключ
 * идемпотентности уходит с каждой попыткой, а SENT-сообщение второй раз не берётся.
 *
 * <p>Перед отправкой сообщение сверяется с записью: без действующего согласия пациента — SKIPPED;
 * запись отменена, перенесена, неявка или время прошло — CANCELLED. Перенос сам в outbox ничего не
 * кладёт: доставщик находит его и ставит подтверждение и напоминание на новое время.
 */
@Component
class OutboxDispatcher {
  static final int MAX_ATTEMPTS = 5;
  static final Duration NOT_CONFIGURED_RECHECK = Duration.ofMinutes(10);
  static final Duration STUCK_AFTER = Duration.ofMinutes(10);
  private static final List<Duration> BACKOFF =
      List.of(
          Duration.ofMinutes(1),
          Duration.ofMinutes(5),
          Duration.ofMinutes(15),
          Duration.ofHours(1),
          Duration.ofHours(6));
  private static final Set<String> DUE = Set.of("PENDING", "FAILED");
  private static final Set<String> UNSENT = Set.of("PENDING", "FAILED");
  private static final Set<String> BEFORE_VISIT = Set.of("BOOKED", "CONFIRMED");
  private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

  private final OutboxRepository outbox;
  private final OutboxService service;
  private final NoticeSource notices;
  private final NoticeText text;
  private final NotificationPort notifications;
  private final FiscalPort fiscal;
  private final AuditService audit;
  private final TransactionTemplate tx;

  OutboxDispatcher(
      OutboxRepository outbox,
      OutboxService service,
      NoticeSource notices,
      NoticeText text,
      NotificationPort notifications,
      FiscalPort fiscal,
      AuditService audit,
      TransactionTemplate tx) {
    this.outbox = outbox;
    this.service = service;
    this.notices = notices;
    this.text = text;
    this.notifications = notifications;
    this.fiscal = fiscal;
    this.audit = audit;
    this.tx = tx;
  }

  /**
   * Один проход: переносы, зависшие попытки, затем до 50 очередных сообщений. Возвращает, сколько
   * сообщений взято в работу (не только доставленных).
   */
  int runOnce(Instant now) {
    reconcileRescheduled(now);
    recoverStuck(now);
    var taken = 0;
    for (var candidate :
        outbox.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(DUE, now)) {
      if (candidate.attempts >= MAX_ATTEMPTS) continue;
      taken++;
      try {
        process(candidate.id, now);
      } catch (RuntimeException ex) {
        // Сообщение и адрес в лог не пишем: только id строки и тип ошибки.
        log.warn(
            "outbox_delivery_error id={} error={}", candidate.id, ex.getClass().getSimpleName());
      }
    }
    return taken;
  }

  /** Подтверждение и напоминание на старое время отменяются, ставятся новые — на новое. */
  void reconcileRescheduled(Instant now) {
    for (var appointmentId : notices.rescheduledAppointments(now)) {
      tx.executeWithoutResult(
          status -> {
            var notice = notices.load(appointmentId).orElse(null);
            if (notice == null) return;
            UUID clinic = null;
            for (var old :
                outbox.findByAggregateIdAndTemplateInAndStatusIn(
                    appointmentId, NoticeTemplates.ABOUT_TIME, UNSENT)) {
              clinic = old.clinicId;
              if (!notice.startsAt().equals(old.appointmentStartsAt)) {
                close(old, "CANCELLED", "RESCHEDULED", now);
              }
            }
            if (clinic == null) {
              clinic =
                  outbox.findByAggregateIdOrderByCreatedAt(appointmentId).stream()
                      .map(m -> m.clinicId)
                      .findFirst()
                      .orElse(null);
            }
            if (clinic != null) service.scheduleFor(appointmentId, notice.startsAt(), clinic, now);
          });
    }
  }

  /**
   * Попытка начата и не закончена — процесс остановили посреди отправки. Вебхук получает тот же
   * ключ идемпотентности и повтор отбросит сам; у Telegram такого ключа нет, поэтому без повтора.
   */
  void recoverStuck(Instant now) {
    for (var stuck : outbox.findByStatusAndLastAttemptAtBefore("SENDING", now.minus(STUCK_AFTER))) {
      tx.executeWithoutResult(
          status -> {
            var message = outbox.findById(stuck.id).orElseThrow();
            if (!"SENDING".equals(message.status)) return;
            message.lastError = "DELIVERY_UNCERTAIN";
            if (TelegramChannel.NAME.equals(message.channel)
                || MaxChannel.NAME.equals(message.channel)) {
              message.status = "FAILED";
              message.attempts = MAX_ATTEMPTS;
              message.nextAttemptAt = null;
            } else {
              retryLater(message, now);
            }
          });
    }
  }

  /** true — сообщение доставлено этой попыткой. */
  private boolean process(UUID id, Instant now) {
    // Шаг 1, транзакция: сверка с записью и пометка попытки. Занятое другим доставщиком — мимо.
    var prepared = tx.execute(status -> prepare(id, now));
    if (prepared == null) return false;
    // Шаг 2, без транзакции: сама отправка.
    var result = send(prepared);
    // Шаг 3, транзакция: итог.
    tx.executeWithoutResult(status -> finish(id, result, now));
    if (result.outcome() == DeliveryOutcome.SENT) {
      audit.record(
          prepared.clinicId(),
          NoticeTemplates.FISCALIZATION.equals(prepared.topic())
              ? "FISCAL_RECEIPT_SENT"
              : "NOTIFICATION_DELIVERED",
          "OUTBOX",
          id,
          "ALLOWED",
          prepared.template() + ":" + result.channel().toLowerCase());
      return true;
    }
    return false;
  }

  private Prepared prepare(UUID id, Instant now) {
    var message = outbox.findById(id).orElse(null);
    if (message == null || !DUE.contains(message.status)) return null;
    if (NoticeTemplates.FISCALIZATION.equals(message.topic)) {
      message.status = "SENDING";
      message.channel = FiscalResult.CHANNEL;
      message.lastAttemptAt = now;
      return new Prepared(message.topic, message.template, message.clinicId, message, null);
    }
    var notice = notices.load(message.aggregateId).orElse(null);
    var skip = skipReason(message, notice, now);
    if (skip != null) {
      close(message, "NO_CONSENT".equals(skip) ? "SKIPPED" : "CANCELLED", skip, now);
      return null;
    }
    var patientMessage = text.render(message.template, message.idempotencyKey, notice);
    message.status = "SENDING";
    message.channel = notifications.channelFor(patientMessage);
    message.lastAttemptAt = now;
    return new Prepared(message.topic, message.template, message.clinicId, message, patientMessage);
  }

  /** Почему сообщение о записи отправлять нельзя; null — можно. */
  static String skipReason(OutboxMessage message, AppointmentNotice notice, Instant now) {
    if (notice == null) return "APPOINTMENT_NOT_FOUND";
    if (!notice.activeConsent()) return "NO_CONSENT";
    if (!notice.startsAt().isAfter(now)) return "EXPIRED";
    if (NoticeTemplates.CANCELLED.equals(message.template)) {
      return "CANCELLED".equals(notice.status()) ? null : "NOT_CANCELLED";
    }
    if ("CANCELLED".equals(notice.status())) return "APPOINTMENT_CANCELLED";
    if ("NO_SHOW".equals(notice.status())) return "NO_SHOW";
    if (!BEFORE_VISIT.contains(notice.status())) return "NOT_ACTIVE";
    if (!notice.startsAt().equals(message.appointmentStartsAt)) return "RESCHEDULED";
    return null;
  }

  private DeliveryResult send(Prepared prepared) {
    try {
      if (prepared.patientMessage() == null) {
        var message = prepared.message();
        return (NoticeTemplates.REFUND_RECEIPT.equals(message.template)
                ? fiscal.sendRefund(message.aggregateId, message.idempotencyKey)
                : fiscal.sendReceipt(message.aggregateId, message.idempotencyKey))
            .asDelivery();
      }
      return notifications.deliver(prepared.patientMessage());
    } catch (RuntimeException ex) {
      return DeliveryResult.retry(prepared.message().channel, "DELIVERY_ERROR");
    }
  }

  private void finish(UUID id, DeliveryResult result, Instant now) {
    var message = outbox.findById(id).orElseThrow();
    message.channel = result.channel();
    switch (result.outcome()) {
      case SENT -> {
        message.status = "SENT";
        message.sentAt = now;
        message.lastError = null;
        message.nextAttemptAt = null;
      }
      case NOT_CONFIGURED -> {
        // Канал не настроен: не ошибка доставки — ждём настройки, попытки не тратим.
        message.status = "PENDING";
        message.lastError = result.reason();
        message.nextAttemptAt = now.plus(NOT_CONFIGURED_RECHECK);
      }
      case RETRY -> {
        message.lastError = result.reason();
        retryLater(message, now);
      }
      case REJECTED -> {
        message.status = "FAILED";
        message.lastError = result.reason();
        message.attempts = MAX_ATTEMPTS;
        message.nextAttemptAt = null;
      }
    }
  }

  private static void retryLater(OutboxMessage message, Instant now) {
    message.status = "FAILED";
    message.attempts++;
    message.nextAttemptAt =
        message.attempts >= MAX_ATTEMPTS
            ? null
            : now.plus(BACKOFF.get(Math.min(message.attempts - 1, BACKOFF.size() - 1)));
  }

  private static void close(OutboxMessage message, String status, String reason, Instant now) {
    message.status = status;
    message.lastError = reason;
    message.lastAttemptAt = now;
    message.nextAttemptAt = null;
  }
}
