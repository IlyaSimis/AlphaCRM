package ru.quietclinic.communications;

import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.Permission;
import ru.quietclinic.platform.PlatformConfig;

/**
 * Последние внешние выводы своей клиники — администраторам и техподдержке (без текста и адресата:
 * их в строке outbox нет по построению). {@code ?status=FAILED} — только строки с этим статусом
 * (например, для блока «Сегодня»).
 */
@RestController
@RequestMapping("/api/v1/outbox")
public class OutboxController {
  private final OutboxRepository outbox;
  private final AuditService audit;

  OutboxController(OutboxRepository outbox, AuditService audit) {
    this.outbox = outbox;
    this.audit = audit;
  }

  /**
   * Повторить доставку сейчас: неотправленная строка (PENDING или FAILED) получает срок «сейчас»,
   * доставщик возьмёт её следующим проходом. Отправленные и отменённые не повторяются.
   */
  @PostMapping("/{id}/retry")
  @Transactional
  OutboxView retry(@PathVariable UUID id) {
    requirePermission(Permission.DELIVERY_RETRY);
    OutboxMessage message =
        outbox
            .findById(id)
            .filter(m -> m.clinicId.equals(clinicId()))
            .orElseThrow(
                () ->
                    PlatformConfig.notFound(
                        "OUTBOX_MESSAGE_NOT_FOUND", "Outbox message not found"));
    if (!Set.of("PENDING", "FAILED").contains(message.status)) {
      throw conflict("OUTBOX_NOT_RETRYABLE", "Only pending or failed messages can be retried");
    }
    message.nextAttemptAt = Instant.now();
    audit.record(
        PlatformConfig.actorId(),
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
    String wanted = status.trim().toUpperCase(Locale.ROOT);
    if (!STATUSES.contains(wanted)) {
      throw conflict("INVALID_OUTBOX_STATUS", "Unknown outbox status");
    }
    return outbox.findTop100ByClinicIdAndStatusOrderByCreatedAtDesc(clinicId(), wanted).stream()
        .map(OutboxView::of)
        .toList();
  }
}
