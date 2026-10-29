package ru.quietclinic.assistant;

import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import org.springframework.stereotype.Service;
import ru.quietclinic.assistant.AssistantController.NavigateRequest;
import ru.quietclinic.assistant.AssistantController.NavigationView;
import ru.quietclinic.assistant.AssistantController.StatusView;
import ru.quietclinic.commercial.CommercialService;
import ru.quietclinic.platform.Permission;

@Service
class AssistantService {
  private final AssistantGateway gateway;
  private final CommercialService commercial;

  AssistantService(AssistantGateway gateway, CommercialService commercial) {
    this.gateway = gateway;
    this.commercial = commercial;
  }

  StatusView status() {
    requirePermission(Permission.ASSISTANT_USE);
    return new StatusView(gateway.enabled(), gateway.model(), commercial.usage());
  }

  NavigationView navigate(NavigateRequest request) {
    requirePermission(Permission.ASSISTANT_USE);
    if (!gateway.enabled()) {
      throw conflict("ASSISTANT_NOT_CONFIGURED", "AI navigation provider is not configured");
    }
    commercial.reserveAiRequest();
    try {
      var result = gateway.navigate(request.query(), request.currentPage(), request.destinations());
      var allowed =
          request.destinations().stream()
              .anyMatch(item -> item.id().equals(result.destinationId()));
      if (!allowed) {
        throw conflict(
            "ASSISTANT_UNKNOWN_DESTINATION", "AI returned a destination outside the catalog");
      }
      commercial.recordTokens(result.inputTokens(), result.outputTokens());
      return new NavigationView(result.destinationId(), "AI", commercial.usage());
    } catch (RuntimeException error) {
      commercial.releaseAiRequest();
      throw error;
    }
  }
}
