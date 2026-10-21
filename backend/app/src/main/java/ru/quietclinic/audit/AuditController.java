package ru.quietclinic.audit;

import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
  private final AuditService audit;

  AuditController(AuditService audit) {
    this.audit = audit;
  }

  /** Последние события аудита. Клинический текст в журнал не попадает по построению. */
  @GetMapping
  List<AuditView> recent(
      @RequestParam(required = false) String result,
      @RequestParam(required = false) String objectType) {
    requireRole("MANAGER", "ADMINISTRATOR");
    return audit.recent().stream()
        .filter(e -> result == null || result.isBlank() || e.result().equals(result))
        .filter(
            e -> objectType == null || objectType.isBlank() || e.objectType().equals(objectType))
        .toList();
  }
}
