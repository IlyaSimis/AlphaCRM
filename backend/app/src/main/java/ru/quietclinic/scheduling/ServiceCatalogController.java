package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Справочник услуг: из него форма записи берёт название, длительность и цену. */
@RestController
@RequestMapping("/api/v1/services")
public class ServiceCatalogController {
  private final ClinicServiceRepository services;

  ServiceCatalogController(ClinicServiceRepository services) {
    this.services = services;
  }

  @GetMapping
  @Transactional(readOnly = true)
  List<ServiceView> list() {
    requireRole("ADMINISTRATOR", "DOCTOR", "NURSE", "CASHIER", "MANAGER");
    return services.findAllByOrderByDurationMinutesDesc().stream().map(ServiceView::of).toList();
  }
}
