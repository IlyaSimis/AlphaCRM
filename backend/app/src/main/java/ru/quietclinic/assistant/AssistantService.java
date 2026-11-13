package ru.quietclinic.assistant;

import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import org.springframework.stereotype.Service;
import ru.quietclinic.assistant.AssistantController.NavigateRequest;
import ru.quietclinic.assistant.AssistantController.NavigationView;
import ru.quietclinic.assistant.AssistantController.StatusView;
import ru.quietclinic.platform.Permission;

@Service
class AssistantService {
  private final AssistantGateway gateway;

  AssistantService(AssistantGateway gateway) {
    this.gateway = gateway;
  }

  StatusView status() {
    requirePermission(Permission.ASSISTANT_USE);
    return new StatusView(gateway.enabled(), gateway.model());
  }

  NavigationView navigate(NavigateRequest request) {
    requirePermission(Permission.ASSISTANT_USE);
    if (!gateway.enabled()) {
      throw conflict("ASSISTANT_NOT_CONFIGURED", "AI navigation provider is not configured");
    }
    AssistantGateway.GatewayResult result =
        gateway.navigate(request.query(), request.currentPage(), request.destinations());
    boolean allowed =
        request.destinations().stream().anyMatch(item -> item.id().equals(result.destinationId()));
    if (!allowed) {
      throw conflict(
          "ASSISTANT_UNKNOWN_DESTINATION", "AI returned a destination outside the catalog");
    }
    return new NavigationView(result.destinationId(), "AI");
  }
}
