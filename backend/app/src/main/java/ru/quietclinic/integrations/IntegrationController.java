package ru.quietclinic.integrations;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Настройки интеграций текущей клиники. Секреты принимаются, но никогда не возвращаются. */
@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {
  private final IntegrationService integrations;

  IntegrationController(IntegrationService integrations) {
    this.integrations = integrations;
  }

  @GetMapping
  List<IntegrationView> list() {
    return integrations.list();
  }

  @GetMapping("/{type}")
  IntegrationView get(@PathVariable String type) {
    return integrations.get(type);
  }

  @PutMapping("/{type}/settings")
  IntegrationView settings(
      @PathVariable String type, @Valid @RequestBody IntegrationSettingsRequest request) {
    return integrations.updateSettings(type, request);
  }

  @PutMapping("/{type}/secret")
  IntegrationView secret(
      @PathVariable String type, @Valid @RequestBody IntegrationSecretRequest request) {
    return integrations.updateSecret(type, request);
  }

  @PostMapping("/{type}/activate")
  IntegrationView activate(@PathVariable String type) {
    return integrations.activate(type);
  }

  @PostMapping("/{type}/disable")
  IntegrationView disable(@PathVariable String type) {
    return integrations.disable(type);
  }

  @PostMapping("/{type}/webhook/connect")
  IntegrationWebhookStatusView connectWebhook(@PathVariable String type) {
    return integrations.connectWebhook(type);
  }

  @GetMapping("/{type}/webhook/status")
  IntegrationWebhookStatusView webhookStatus(@PathVariable String type) {
    return integrations.webhookStatus(type);
  }
}
