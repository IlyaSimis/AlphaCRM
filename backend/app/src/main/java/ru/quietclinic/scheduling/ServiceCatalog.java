package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.hasRole;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;

/**
 * Справочник услуг клиники. Администратор заводит услуги, меняет цену и выключает их. Новая цена и
 * выключение действуют только на новые записи: снимок названия и цены в записи не меняется.
 */
@Service
class ServiceCatalog {
  private final ClinicServiceRepository services;
  private final AuditService audit;

  ServiceCatalog(ClinicServiceRepository services, AuditService audit) {
    this.services = services;
    this.audit = audit;
  }

  /** Включённые услуги своей клиники; администратор с {@code all} видит и выключенные. */
  @Transactional(readOnly = true)
  List<ServiceView> list(boolean all) {
    requireRole("ADMINISTRATOR", "DOCTOR", "NURSE", "CASHIER", "MANAGER");
    var withInactive = all && hasRole("ADMINISTRATOR");
    return services.findByClinicIdOrderByActiveDescDurationMinutesDescNameAsc(clinicId()).stream()
        .filter(s -> withInactive || s.active)
        .map(ServiceView::of)
        .toList();
  }

  @Transactional
  ServiceView create(CreateServiceRequest request) {
    requireRole("ADMINISTRATOR");
    var name = request.name().trim();
    if (services.existsByClinicIdAndNameIgnoreCase(clinicId(), name)) {
      throw conflict("SERVICE_NAME_TAKEN", "A service with this name already exists");
    }
    try {
      var service =
          services.saveAndFlush(
              new ClinicService(
                  clinicId(), name, request.durationMinutes(), request.priceMinor(), "RUB"));
      audit.record(actorId(), "SERVICE_CREATED", "SERVICE", service.id, "ALLOWED", null);
      return ServiceView.of(service);
    } catch (DataIntegrityViolationException ex) {
      throw conflict("SERVICE_NAME_TAKEN", "A service with this name already exists");
    }
  }

  @Transactional
  ServiceView changePrice(UUID id, ServicePriceRequest request) {
    requireRole("ADMINISTRATOR");
    var service = own(id);
    service.priceMinor = request.priceMinor();
    audit.record(actorId(), "SERVICE_PRICE_CHANGED", "SERVICE", service.id, "ALLOWED", null);
    return ServiceView.of(service);
  }

  @Transactional
  ServiceView setActive(UUID id, boolean active) {
    requireRole("ADMINISTRATOR");
    var service = own(id);
    if (service.active != active) {
      service.active = active;
      audit.record(
          actorId(),
          active ? "SERVICE_ACTIVATED" : "SERVICE_DEACTIVATED",
          "SERVICE",
          service.id,
          "ALLOWED",
          null);
    }
    return ServiceView.of(service);
  }

  private ClinicService own(UUID id) {
    return services
        .findByIdAndClinicId(id, clinicId())
        .orElseThrow(() -> notFound("SERVICE_NOT_FOUND", "Service not found"));
  }
}
