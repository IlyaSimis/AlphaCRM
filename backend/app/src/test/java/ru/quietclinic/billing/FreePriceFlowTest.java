package ru.quietclinic.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.quietclinic.StaffSessions;

/**
 * Свободная цена (сценарий D5): услуга OPEN с пределами, запись без суммы, счёт AWAITING_AMOUNT по
 * событию AppointmentCompleted, оплата только после ввода суммы в пределах.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class FreePriceFlowTest {
  static final String ANNA = "00000000-0000-0000-0000-000000000101";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  private String json(String body, String path) {
    return JsonPath.read(body, path).toString();
  }

  @Test
  void openPriceNeedsLimits() throws Exception {
    mvc.perform(
            post("/api/v1/services")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Open %s","durationMinutes":30,"priceMode":"OPEN","minMinor":100000}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_PRICING"));
    mvc.perform(
            post("/api/v1/services")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Open %s","durationMinutes":30,"priceMode":"OPEN",
                     "minMinor":500000,"maxMinor":100000}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_PRICING"));
  }

  @Test
  void openPriceVisitIsPaidOnlyAfterAmountWithinLimits() throws Exception {
    String name = "Open price " + UUID.randomUUID().toString().substring(0, 8);
    String service =
        mvc.perform(
                post("/api/v1/services")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","durationMinutes":30,"priceMode":"OPEN",
                         "minMinor":100000,"maxMinor":500000,"currency":"RUB"}
                        """
                            .formatted(name)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.priceMode").value("OPEN"))
            .andExpect(jsonPath("$.priceMinor").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String serviceId = json(service, "$.id");
    // Цены меняет администратор и выше; менеджер — нет (раздел C).
    mvc.perform(
            put("/api/v1/services/" + serviceId + "/pricing")
                .with(staff.role("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priceMode\":\"FIXED\",\"priceMinor\":1}"))
        .andExpect(status().isForbidden());

    String booking =
        """
        {"patientId":"%s","practitionerId":"%s","startsAt":"2027-04-06T14:00:00Z",
         "endsAt":"2027-04-06T14:30:00Z","serviceName":"%s","estimateMinor":%d}
        """;
    mvc.perform(
            post("/api/v1/appointments")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(booking.formatted(ANNA, StaffSessions.PETROV, name, 900000)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ESTIMATE_OUT_OF_RANGE"));
    String appointment =
        json(
            mvc.perform(
                    post("/api/v1/appointments")
                        .with(staff.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(booking.formatted(ANNA, StaffSessions.PETROV, name, 200000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priceMode").value("OPEN"))
                .andExpect(jsonPath("$.amountMinor").isEmpty())
                .andExpect(jsonPath("$.estimateMinor").value(200000))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    move(appointment, staff.admin(), "ARRIVED");
    move(appointment, staff.doctor(StaffSessions.PETROV), "COMPLETED");

    // Счёт создаёт касса из события AppointmentCompleted — с пределами и оценкой.
    String charges =
        mvc.perform(get("/api/v1/billing/charges").with(staff.role("CASHIER")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids =
        JsonPath.read(charges, "$[?(@.appointmentId == '" + appointment + "')].chargeId");
    assertThat(ids).hasSize(1);
    String chargeId = ids.get(0);
    mvc.perform(get("/api/v1/billing/charges").with(staff.role("CASHIER")))
        .andExpect(
            jsonPath("$[?(@.chargeId == '" + chargeId + "')].status").value("AWAITING_AMOUNT"))
        .andExpect(jsonPath("$[?(@.chargeId == '" + chargeId + "')].maxMinor").value(500000))
        .andExpect(jsonPath("$[?(@.chargeId == '" + chargeId + "')].estimateMinor").value(200000));

    pay(chargeId, "free-" + chargeId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AMOUNT_REQUIRED"));
    amount(chargeId, staff.role("CASHIER"), 900000)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AMOUNT_OUT_OF_RANGE"));
    amount(chargeId, staff.role("MANAGER"), 250000).andExpect(status().isForbidden());
    amount(chargeId, staff.role("CASHIER"), 250000)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.amountMinor").value(250000));
    pay(chargeId, "free-" + chargeId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chargeStatus").value("PAID"))
        .andExpect(jsonPath("$.amountMinor").value(250000));

    // Событие доставлено и отмечено в inbox потребителя.
    Long published =
        jdbc.queryForObject(
            "select count(*) from domain_event_outbox where type = 'AppointmentCompleted'"
                + " and status = 'PUBLISHED' and payload like ?",
            Long.class,
            "%" + appointment + "%");
    assertThat(published).isEqualTo(1L);
    Long inbox =
        jdbc.queryForObject(
            "select count(*) from domain_event_inbox i join domain_event_outbox e"
                + " on e.id = i.event_id where i.consumer = 'billing.charge-on-completed'"
                + " and e.payload like ?",
            Long.class,
            "%" + appointment + "%");
    assertThat(inbox).isEqualTo(1L);
  }

  private void move(String appointment, RequestPostProcessor who, String target) throws Exception {
    mvc.perform(
            post("/api/v1/appointments/" + appointment + "/status")
                .with(who)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + target + "\"}"))
        .andExpect(status().isOk());
  }

  private ResultActions pay(String chargeId, String key) throws Exception {
    return mvc.perform(
        post("/api/v1/billing/charges/" + chargeId + "/payments")
            .with(staff.role("CASHIER"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"idempotencyKey\":\"" + key + "\"}"));
  }

  private ResultActions amount(String chargeId, RequestPostProcessor who, long value)
      throws Exception {
    return mvc.perform(
        put("/api/v1/billing/charges/" + chargeId + "/amount")
            .with(who)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amountMinor\":" + value + "}"));
  }
}
