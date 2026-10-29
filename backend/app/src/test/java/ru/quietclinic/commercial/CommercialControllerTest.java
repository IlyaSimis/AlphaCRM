package ru.quietclinic.commercial;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.quietclinic.StaffSessions;
import ru.quietclinic.commercial.CommercialPaymentGateway.ProviderCheckout;
import ru.quietclinic.commercial.CommercialPaymentGateway.ProviderPayment;

/**
 * Hosted checkout и публичный вебхук: тариф включается только после сверки платежа с провайдером.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CommercialControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean CommercialPaymentGateway gateway;
  StaffSessions staff;

  @BeforeEach
  void setUp() {
    staff = new StaffSessions(mvc);
    jdbc.update("delete from commercial.payment");
    jdbc.update("delete from commercial.subscription");
    when(gateway.enabled()).thenReturn(true);
    when(gateway.createCheckout(any()))
        .thenAnswer(
            invocation -> {
              var command =
                  invocation.getArgument(0, CommercialPaymentGateway.PaymentCommand.class);
              return new ProviderCheckout(
                  "yookassa-" + command.paymentId(),
                  "https://pay.example.test/" + command.paymentId());
            });
  }

  @Test
  void onlyOwnerReadsSubscription() throws Exception {
    mvc.perform(get("/api/v1/commercial/subscription").with(staff.role("OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planCode").value("FREE"))
        .andExpect(jsonPath("$.checkoutEnabled").value(true))
        .andExpect(jsonPath("$.plans.length()").value(3));
    mvc.perform(get("/api/v1/commercial/subscription").with(staff.exactly("ADMINISTRATOR")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void checkoutReturnsHostedProviderUrl() throws Exception {
    var response =
        mvc.perform(
                post("/api/v1/commercial/subscription/checkout")
                    .with(staff.role("OWNER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"planCode\":\"TEAM\"}"))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.checkoutUrl")
                    .value(org.hamcrest.Matchers.startsWith("https://pay.example.test/")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(JsonPath.read(response, "$.paymentId"));
    var stored =
        jdbc.queryForMap(
            "select amount_minor, status, provider_ref from commercial.payment where id = ?", id);
    org.assertj.core.api.Assertions.assertThat(stored.get("AMOUNT_MINOR")).isEqualTo(199000L);
    org.assertj.core.api.Assertions.assertThat(stored.get("STATUS")).isEqualTo("PENDING");
    org.assertj.core.api.Assertions.assertThat(stored.get("PROVIDER_REF"))
        .isEqualTo("yookassa-" + id);
  }

  @Test
  void verifiedPublicWebhookActivatesPlanWithoutSessionOrCsrf() throws Exception {
    var payment = checkout();
    when(gateway.getPayment("yookassa-" + payment))
        .thenReturn(
            new ProviderPayment("yookassa-" + payment, "succeeded", true, 199000, "RUB", payment));

    mvc.perform(
            post("/api/v1/commercial/payments/yookassa/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"object\":{\"id\":\"yookassa-" + payment + "\"}}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/commercial/subscription").with(staff.role("OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planCode").value("TEAM"))
        .andExpect(jsonPath("$.monthlyAiRequests").value(3000))
        .andExpect(jsonPath("$.payments[0].status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.currentPeriodEnd").isNotEmpty());
  }

  @Test
  void mismatchedVerifiedPaymentDoesNotActivatePlan() throws Exception {
    var payment = checkout();
    when(gateway.getPayment("yookassa-" + payment))
        .thenReturn(
            new ProviderPayment("yookassa-" + payment, "succeeded", true, 1, "RUB", payment));

    mvc.perform(
            post("/api/v1/commercial/payments/yookassa/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"object\":{\"id\":\"yookassa-" + payment + "\"}}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PAYMENT_VERIFICATION_FAILED"));
    mvc.perform(get("/api/v1/commercial/subscription").with(staff.role("OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planCode").value("FREE"))
        .andExpect(jsonPath("$.payments[0].status").value("PENDING"));
  }

  private UUID checkout() throws Exception {
    var response =
        mvc.perform(
                post("/api/v1/commercial/subscription/checkout")
                    .with(staff.role("OWNER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"planCode\":\"TEAM\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(response, "$.paymentId"));
  }
}
