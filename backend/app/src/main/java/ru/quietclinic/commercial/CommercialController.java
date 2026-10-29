package ru.quietclinic.commercial;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.quietclinic.commercial.CommercialService.CheckoutView;
import ru.quietclinic.commercial.CommercialService.SubscriptionView;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/commercial")
public class CommercialController {
  private final CommercialService commercial;

  CommercialController(CommercialService commercial) {
    this.commercial = commercial;
  }

  @GetMapping("/subscription")
  SubscriptionView subscription() {
    return commercial.subscription();
  }

  @PostMapping("/subscription/checkout")
  CheckoutView checkout(@Valid @RequestBody CheckoutRequest request) {
    return commercial.checkout(request);
  }

  /** Публичный приёмник: доверие появляется только после серверного GET платежа у ЮKassa. */
  @PostMapping("/payments/yookassa/webhook")
  void yooKassaWebhook(@RequestBody JsonNode notification) {
    commercial.confirmYooKassa(notification.path("object").required("id").asText());
  }
}
