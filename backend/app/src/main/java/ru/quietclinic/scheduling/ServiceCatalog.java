package ru.quietclinic.scheduling;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.can;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.Permission;

/**
 * Справочник услуг клиники. Владелец и администраторы (разрешение {@code schedule.service.manage})
 * заводят услуги, меняют цену и её режим (фиксированная или свободная) и выключают их. Новая цена и
 * выключение действуют только на новые записи: снимок названия и цены в записи не меняется.
 */
@Service
class ServiceCatalog {
  private final ClinicServiceRepository services;
  private final ServiceNormRepository norms;
  private final AuditService audit;

  ServiceCatalog(
      ClinicServiceRepository services, ServiceNormRepository norms, AuditService audit) {
    this.services = services;
    this.norms = norms;
    this.audit = audit;
  }

  /** Нормы расхода услуги: позиции склада на один приём. */
  @Transactional(readOnly = true)
  List<ServiceNormView> norms(UUID id) {
    requirePermission(Permission.SERVICE_READ);
    ClinicService service = own(id);
    return norms.findByServiceId(service.id).stream().map(ServiceNormView::of).toList();
  }

  /** Замена норм: склад спишет эти позиции при завершении приёма с этой услугой. */
  @Transactional
  List<ServiceNormView> replaceNorms(UUID id, ServiceNormsRequest request) {
    requirePermission(Permission.SERVICE_MANAGE, Permission.INVENTORY_CATALOG_WRITE);
    ClinicService service = own(id);
    Set<UUID> unique = new HashSet<>();
    for (ServiceNormsRequest.Norm norm : request.norms()) {
      if (!unique.add(norm.itemId())) {
        throw conflict("DUPLICATE_NORM_ITEM", "Each item may appear in the norms only once");
      }
    }
    norms.deleteByServiceId(service.id);
    norms.flush();
    for (ServiceNormsRequest.Norm norm : request.norms()) {
      norms.save(new ServiceNorm(service.id, norm.itemId(), norm.quantity()));
    }
    audit.record(
        actorId(),
        "SERVICE_NORMS_CHANGED",
        "SERVICE",
        service.id,
        "ALLOWED",
        "items:" + request.norms().size());
    return norms(id);
  }

  /** Включённые услуги своей клиники; администратор с {@code all} видит и выключенные. */
  @Transactional(readOnly = true)
  List<ServiceView> list(boolean all) {
    requirePermission(Permission.SERVICE_READ);
    boolean withInactive = all && can(Permission.SERVICE_MANAGE);
    return services.findByClinicIdOrderByActiveDescDurationMinutesDescNameAsc(clinicId()).stream()
        .filter(s -> withInactive || s.active)
        .map(ServiceView::of)
        .toList();
  }

  @Transactional
  ServiceView create(CreateServiceRequest request) {
    requirePermission(Permission.SERVICE_MANAGE);
    String name = request.name().trim();
    if (services.existsByClinicIdAndNameIgnoreCase(clinicId(), name)) {
      throw conflict("SERVICE_NAME_TAKEN", "A service with this name already exists");
    }
    try {
      ClinicService draft = new ClinicService(clinicId(), name, request.durationMinutes(), "RUB");
      draft.setPricing(
          request.priceMode(), request.priceMinor(), request.minMinor(), request.maxMinor());
      ClinicService service = services.saveAndFlush(draft);
      audit.record(actorId(), "SERVICE_CREATED", "SERVICE", service.id, "ALLOWED", null);
      return ServiceView.of(service);
    } catch (DataIntegrityViolationException ex) {
      throw conflict("SERVICE_NAME_TAKEN", "A service with this name already exists");
    }
  }

  @Transactional
  ServiceView changePrice(UUID id, ServicePriceRequest request) {
    return changePricing(
        id, new ServicePricingRequest(ClinicService.FIXED, request.priceMinor(), null, null));
  }

  /** Режим цены и сумма или пределы. В аудит — режим, без сумм. */
  @Transactional
  ServiceView changePricing(UUID id, ServicePricingRequest request) {
    requirePermission(Permission.SERVICE_MANAGE);
    ClinicService service = own(id);
    service.setPricing(
        request.priceMode(), request.priceMinor(), request.minMinor(), request.maxMinor());
    audit.record(
        actorId(), "SERVICE_PRICE_CHANGED", "SERVICE", service.id, "ALLOWED", service.priceMode);
    return ServiceView.of(service);
  }

  @Transactional
  ServiceView setActive(UUID id, boolean active) {
    requirePermission(Permission.SERVICE_MANAGE);
    ClinicService service = own(id);
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
