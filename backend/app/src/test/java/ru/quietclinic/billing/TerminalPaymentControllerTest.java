package ru.quietclinic.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

@SpringBootTest(
    properties = {
      "clinic.terminal.provider=test-terminal",
      "clinic.terminal.webhook-secret=test-terminal-secret-32-characters-long"
    })
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TerminalPaymentControllerTest {
  private static final String PETROV = "00000000-0000-0000-0000-000000000201";
  private static final String SECRET = "test-terminal-secret-32-characters-long";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean TerminalPaymentGateway gateway;
  StaffSessions staff;

  @BeforeEach
  void setUp() {
    staff = new StaffSessions(mvc);
    when(gateway.configured()).thenReturn(true);
    when(gateway.start(any()))
        .thenAnswer(
            call ->
                "terminal-ref-" + call.getArgument(0, TerminalPaymentCommand.class).paymentId());
  }

  private String charge(String startsAt, String endsAt) throws Exception {
    var appointment =
        mvc.perform(
                post("/api/v1/appointments")
                    .with(staff.role("ADMINISTRATOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"patientId":"00000000-0000-0000-0000-000000000102","practitionerId":"%s",
                         "startsAt":"%s","endsAt":"%s","serviceName":"Повторный приём"}
                        """
                            .formatted(PETROV, startsAt, endsAt)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var created =
        mvc.perform(
                post("/api/v1/billing/charges")
                    .with(staff.role("CASHIER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"appointmentId\":\""
                            + JsonPath.read(appointment, "$.id")
                            + "\",\"amountMinor\":300000,\"currency\":\"RUB\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(created, "$.chargeId");
  }

  private StartedPayment start(String chargeId, String method) throws Exception {
    var body =
        mvc.perform(
                post("/api/v1/billing/charges/" + chargeId + "/terminal-payments")
                    .with(staff.role("CASHIER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"idempotencyKey\":\"terminal-"
                            + UUID.randomUUID()
                            + "\",\"method\":\""
                            + method
                            + "\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.chargeStatus").value("PENDING"))
            .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(body, "$.paymentId");
    return new StartedPayment(
        id,
        jdbc.queryForObject(
            "select provider_ref from payment_record where id = ?",
            String.class,
            UUID.fromString(id)));
  }

  private void event(String eventId, String reference, String paymentStatus, boolean duplicate)
      throws Exception {
    var body =
        "{\"eventId\":\""
            + eventId
            + "\",\"paymentRef\":\""
            + reference
            + "\",\"status\":\""
            + paymentStatus
            + "\"}";
    mvc.perform(
            post("/api/v1/integrations/terminal/events")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Terminal-Signature", sign(body))
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted").value(true))
        .andExpect(jsonPath("$.duplicate").value(duplicate))
        .andExpect(jsonPath("$.paymentStatus").value(paymentStatus));
  }

  @Test
  void successPaysChargeAndRepeatedEventDoesNotPublishInvoicePaidTwice() throws Exception {
    var chargeId = charge("2026-12-01T13:00:00Z", "2026-12-01T13:30:00Z");
    var payment = start(chargeId, "CARD");
    event("terminal-success-1", payment.reference(), "SUCCEEDED", false);
    event("terminal-success-1", payment.reference(), "SUCCEEDED", true);

    assertThat(
            jdbc.queryForObject(
                "select status from charge where id = ?", String.class, UUID.fromString(chargeId)))
        .isEqualTo("PAID");
    assertThat(
            jdbc.queryForObject(
                "select fiscal_status from payment_record where id = ?",
                String.class,
                UUID.fromString(payment.id())))
        .isEqualTo("QUEUED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from domain_event_outbox where type = 'InvoicePaid' and payload like ?",
                Integer.class,
                "%" + payment.id() + "%"))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from outbox_message where aggregate_id = ?",
                Integer.class,
                UUID.fromString(payment.id())))
        .isEqualTo(1);
  }

  @Test
  void cancellationFailureAndTimeoutNeverMarkChargePaid() throws Exception {
    var statuses = new String[] {"CANCELLED", "FAILED", "TIMEOUT"};
    for (int index = 0; index < statuses.length; index++) {
      var chargeId =
          charge(
              "2026-12-01T" + "%02d".formatted(index + 9) + ":00:00Z",
              "2026-12-01T" + "%02d".formatted(index + 9) + ":30:00Z");
      var payment = start(chargeId, "SBP");
      event("terminal-final-" + statuses[index], payment.reference(), statuses[index], false);
      assertThat(
              jdbc.queryForObject(
                  "select status from charge where id = ?",
                  String.class,
                  UUID.fromString(chargeId)))
          .isEqualTo("PENDING");
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from domain_event_outbox where type = 'InvoicePaid' and payload like ?",
                  Integer.class,
                  "%" + payment.id() + "%"))
          .isZero();
    }
  }

  @Test
  void rejectsUnsignedResultAndTerminalStartWithoutFinancePermission() throws Exception {
    var chargeId = charge("2026-12-03T09:00:00Z", "2026-12-03T09:30:00Z");
    var body = "{\"eventId\":\"forged\",\"paymentRef\":\"missing\",\"status\":\"SUCCEEDED\"}";
    mvc.perform(
            post("/api/v1/integrations/terminal/events")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Terminal-Signature", "00".repeat(32))
                .content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/billing/charges/" + chargeId + "/terminal-payments")
                .with(staff.role("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"manager-terminal\",\"method\":\"CARD\"}"))
        .andExpect(status().isForbidden());
  }

  private static String sign(String body) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }

  private record StartedPayment(String id, String reference) {}
}
