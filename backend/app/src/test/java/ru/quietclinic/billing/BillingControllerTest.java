package ru.quietclinic.billing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.quietclinic.StaffSessions;

/** Касса: список счетов, ручной счёт без дублей, оплата только кассиром. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class BillingControllerTest {
  static final String PETROV = "00000000-0000-0000-0000-000000000201";

  @Autowired MockMvc mvc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  private String book(String startsAt, String endsAt) throws Exception {
    String body =
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
    return JsonPath.read(body, "$.id");
  }

  private ResultActions createCharge(String role, String appointmentId) throws Exception {
    return mvc.perform(
        post("/api/v1/billing/charges")
            .with(staff.role(role))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"appointmentId":"%s","amountMinor":300000,"currency":"RUB"}
                """
                    .formatted(appointmentId)));
  }

  private ResultActions pay(String role, String chargeId, String key) throws Exception {
    return mvc.perform(
        post("/api/v1/billing/charges/" + chargeId + "/payments")
            .with(staff.role(role))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"idempotencyKey\":\"" + key + "\"}"));
  }

  @Test
  void administratorIssuesOneChargeAndPaymentNeedsFinanceRole() throws Exception {
    String appointment = book("2026-11-24T09:00:00Z", "2026-11-24T09:30:00Z");

    createCharge("DOCTOR", appointment).andExpect(status().isForbidden());
    String created =
        createCharge("ADMINISTRATOR", appointment)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.appointmentId").value(appointment))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String chargeId = JsonPath.read(created, "$.chargeId");
    createCharge("ADMINISTRATOR", appointment)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CHARGE_ALREADY_EXISTS"));

    // Оплату принимают кассир и администратор (finance.invoice.write); менеджер и врач — нет.
    pay("MANAGER", chargeId, "pay-" + chargeId).andExpect(status().isForbidden());
    pay("DOCTOR", chargeId, "pay-" + chargeId).andExpect(status().isForbidden());
    pay("CASHIER", chargeId, "pay-" + chargeId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chargeStatus").value("PAID"));
    pay("CASHIER", chargeId, "pay-" + chargeId).andExpect(status().isOk());

    mvc.perform(get("/api/v1/billing/charges").with(staff.role("CASHIER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.chargeId == '" + chargeId + "')].status").value("PAID"))
        .andExpect(
            jsonPath("$[?(@.chargeId == '" + chargeId + "')].paymentStatus")
                .value("MOCK_NO_PROVIDER"))
        // Касса не подключена: чек стоит в очереди и не выдаётся за отправленный.
        .andExpect(jsonPath("$[?(@.chargeId == '" + chargeId + "')].fiscalStatus").value("QUEUED"));
  }

  @Test
  void chargeListIsClosedForDoctorsAndReadOnlyForManagers() throws Exception {
    mvc.perform(get("/api/v1/billing/charges").with(staff.role("DOCTOR")))
        .andExpect(status().isForbidden());
    // Менеджер видит счета только на чтение (раздел C: finance — R).
    mvc.perform(get("/api/v1/billing/charges").with(staff.role("MANAGER")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/billing/charges").with(staff.role("ADMINISTRATOR")))
        .andExpect(status().isOk());
  }
}
