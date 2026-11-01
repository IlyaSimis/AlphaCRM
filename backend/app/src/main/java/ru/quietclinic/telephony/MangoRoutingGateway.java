package ru.quietclinic.telephony;

import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.quietclinic.integrations.MangoClient;
import ru.quietclinic.integrations.MangoIntegrationClientFactory;

/** DB-конфигурация клиники важнее env; legacy используется только при отсутствии записи. */
@Component
class MangoRoutingGateway {
  private final MangoIntegrationClientFactory integrations;
  private final MangoGateway legacy;

  MangoRoutingGateway(MangoIntegrationClientFactory integrations, MangoGateway legacy) {
    this.integrations = integrations;
    this.legacy = legacy;
  }

  MangoClient forClinic(UUID clinicId) {
    return integrations.forClinic(clinicId).orElseGet(this::legacyClient);
  }

  private MangoClient legacyClient() {
    return new MangoClient() {
      @Override
      public boolean configured() {
        return legacy.configured();
      }

      @Override
      public String defaultExtension() {
        return legacy.defaultExtension();
      }

      @Override
      public void callback(String commandId, String extension, String phone) {
        legacy.callback(commandId, extension, phone);
      }
    };
  }
}
