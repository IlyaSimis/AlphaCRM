package ru.quietclinic.communications;

import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.quietclinic.platform.Permission;

/**
 * Последние внешние выводы своей клиники — администраторам и техподдержке (без текста и адресата:
 * их в строке outbox нет по построению). {@code ?status=FAILED} — только строки с этим статусом
 * (например, для блока «Сегодня»).
 */
@RestController
@RequestMapping("/api/v1/outbox")
public class OutboxController {
  private final OutboxRepository outbox;
  private final ru.quietclinic.audit.AuditService audit;

  OutboxController(OutboxRepository outbox, ru.quietclinic.audit.AuditService audit) {
    this.outbox = outbox;
    this.audit = audit;
  }

  /**
   * Повторить доставку сейчас: неотправленная строка (PENDING или FAILED) получает срок «сейчас»,
   * доставщик возьмёт её следующим проходом. Отправленные и отменённые не повторяются.
   */
  @org.springframework.web.bind.annotation.PostMapping("/{id}/retry")
  @Transactional
  OutboxView retry(@org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
    requirePermission(Permission.DELIVERY_RETRY);
    var message =
        outbox
            .findById(id)
            .filter(m -> m.clinicId.equals(ru.quietclinic.platform.PlatformConfig.clinicId()))
            .orElseThrow(
                () ->
                    ru.quietclinic.platform.PlatformConfig.notFound(
                        "OUTBOX_MESSAGE_NOT_FOUND", "Outbox message not found"));
    if (!Set.of("PENDING", "FAILED").contains(message.status)) {
      throw conflict("OUTBOX_NOT_RETRYABLE", "Only pending or failed messages can be retried");
    }
    message.nextAttemptAt = java.time.Instant.now();
    audit.record(
        ru.quietclinic.platform.PlatformConfig.actorId(),
        "OUTBOX_RETRY_REQUESTED",
        "OUTBOX",
        message.id,
        "ALLOWED",
        message.template);
    return OutboxView.of(message);
  }

  private static final Set<String> STATUSES =
      Set.of("PENDING", "SENDING", "SENT", "FAILED", "CANCELLED", "SKIPPED");

  @GetMapping
  @Transactional(readOnly = true)
  List<OutboxView> recent(@RequestParam(required = false) String status) {
    requirePermission(Permission.DELIVERY_READ);
    if (status == null || status.isBlank()) {
      return outbox.findTop100ByClinicIdOrderByCreatedAtDesc(clinicId()).stream()
          .map(OutboxView::of)
          .toList();
    }
    var wanted = status.trim().toUpperCase(Locale.ROOT);
    if (!STATUSES.contains(wanted)) {
      throw conflict("INVALID_OUTBOX_STATUS", "Unknown outbox status");
    }
    return outbox.findTop100ByClinicIdAndStatusOrderByCreatedAtDesc(clinicId(), wanted).stream()
        .map(OutboxView::of)
        .toList();
  }
}
