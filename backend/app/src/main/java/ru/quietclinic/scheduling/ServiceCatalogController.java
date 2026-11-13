package ru.quietclinic.scheduling;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Справочник услуг: из него форма записи берёт название, длительность и цену. */
@RestController
@RequestMapping("/api/v1/services")
public class ServiceCatalogController {
  private final ServiceCatalog services;

  ServiceCatalogController(ServiceCatalog services) {
    this.services = services;
  }

  @GetMapping
  List<ServiceView> list(@RequestParam(defaultValue = "false") boolean all) {
    return services.list(all);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ServiceView create(@Valid @RequestBody CreateServiceRequest request) {
    return services.create(request);
  }

  @PutMapping("/{id}/price")
  ServiceView changePrice(@PathVariable UUID id, @Valid @RequestBody ServicePriceRequest request) {
    return services.changePrice(id, request);
  }

  @PutMapping("/{id}/pricing")
  ServiceView changePricing(
      @PathVariable UUID id, @Valid @RequestBody ServicePricingRequest request) {
    return services.changePricing(id, request);
  }

  @GetMapping("/{id}/consumption-norms")
  List<ServiceNormView> norms(@PathVariable UUID id) {
    return services.norms(id);
  }

  @PutMapping("/{id}/consumption-norms")
  List<ServiceNormView> replaceNorms(
      @PathVariable UUID id, @Valid @RequestBody ServiceNormsRequest request) {
    return services.replaceNorms(id, request);
  }

  @PostMapping("/{id}/deactivate")
  ServiceView deactivate(@PathVariable UUID id) {
    return services.setActive(id, false);
  }

  @PostMapping("/{id}/activate")
  ServiceView activate(@PathVariable UUID id) {
    return services.setActive(id, true);
  }
}
