package ru.quietclinic.communications;

import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Последние внешние выводы своей клиники — только администратору. */
@RestController
@RequestMapping("/api/v1/outbox")
public class OutboxController {
  private final OutboxRepository outbox;

  OutboxController(OutboxRepository outbox) {
    this.outbox = outbox;
  }

  @GetMapping
  @Transactional(readOnly = true)
  List<OutboxView> recent() {
    requireRole("ADMINISTRATOR");
    return outbox.findTop100ByClinicIdOrderByCreatedAtDesc(clinicId()).stream()
        .map(OutboxView::of)
        .toList();
  }
}
