package ru.quietclinic.communications;

import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Последние внешние выводы своей клиники — только администратору. {@code ?status=FAILED} — только
 * строки с этим статусом (например, для блока «Сегодня»).
 */
@RestController
@RequestMapping("/api/v1/outbox")
public class OutboxController {
  private final OutboxRepository outbox;

  OutboxController(OutboxRepository outbox) {
    this.outbox = outbox;
  }

  private static final Set<String> STATUSES =
      Set.of("PENDING", "SENDING", "SENT", "FAILED", "CANCELLED", "SKIPPED");

  @GetMapping
  @Transactional(readOnly = true)
  List<OutboxView> recent(@RequestParam(required = false) String status) {
    requireRole("ADMINISTRATOR");
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
