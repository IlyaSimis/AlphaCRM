package ru.quietclinic.tenancy;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Control-plane API; доступ только по отдельному provisioning token, не по клинической сессии. */
@RestController
@RequestMapping("/api/v1/tenancy/tenants")
class TenantController {
  private final TenantProvisioningService provisioning;

  TenantController(TenantProvisioningService provisioning) {
    this.provisioning = provisioning;
  }

  @GetMapping
  List<TenantRoute> list() {
    return provisioning.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  TenantRoute provision(@Valid @RequestBody CreateTenantRequest request) {
    return provisioning.provision(request);
  }
}
