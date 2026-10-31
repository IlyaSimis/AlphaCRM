package ru.quietclinic.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
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
 * Финансы (раздел C, finance): аннулирование неоплаченного счёта, полный возврат оплаты с чеком
 * возврата в outbox, отчёт о продажах. Возврат и аннулирование — администраторы, не кассир.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class FinanceFlowTest {
  static final String ANNA = "00000000-0000-0000-0000-000000000101";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void voidAndRefund() throws Exception {
    var paidCharge = completedVisitCharge("2027-04-13T10:00:00Z", "2027-04-13T10:30:00Z");
    var openCharge = completedVisitCharge("2027-04-20T10:00:00Z", "2027-04-20T10:30:00Z");

    var paid =
        mvc.perform(
                post("/api/v1/billing/charges/" + paidCharge + "/payments")
                    .with(staff.role("CASHIER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idempotencyKey\":\"fin-" + paidCharge + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String paymentId = JsonPath.read(paid, "$.paymentId");

    // Кассир принимает оплату, но возвращать и аннулировать не может.
    refund(paymentId, staff.role("CASHIER"), "k-" + paymentId).andExpect(status().isForbidden());
    voidCharge(openCharge, staff.role("CASHIER")).andExpect(status().isForbidden());

    // Оплаченный счёт не аннулируется — только возвратом.
    voidCharge(paidCharge, staff.admin())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CHARGE_PAID"));
    var refundId =
        JsonPath.read(
                refund(paymentId, staff.admin(), "k-" + paymentId)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.amountMinor").value(450000))
                    .andExpect(jsonPath("$.reason").value("PATIENT_REQUEST"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(),
                "$.refundId")
            .toString();
    // Повтор с тем же ключом — тот же возврат; новый ключ — второй возврат запрещён.
    refund(paymentId, staff.admin(), "k-" + paymentId)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.refundId").value(refundId));
    refund(paymentId, staff.admin(), "other-" + paymentId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_REFUNDED"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from outbox_message where template = 'refund-receipt'"
                    + " and aggregate_id = ?",
                Long.class,
                UUID.fromString(refundId)))
        .isEqualTo(1L);

    voidCharge(openCharge, staff.admin())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VOIDED"))
        .andExpect(jsonPath("$.voidReason").value("DUPLICATE"));
    mvc.perform(
            post("/api/v1/billing/charges/" + openCharge + "/payments")
                .with(staff.role("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotencyKey\":\"fin-" + openCharge + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CHARGE_VOIDED"));

    var charges =
        mvc.perform(get("/api/v1/billing/charges").with(staff.role("CASHIER")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> statuses =
        JsonPath.read(charges, "$[?(@.chargeId == '" + paidCharge + "')].status");
    assertThat(statuses).containsExactly("REFUNDED");

    // Отчёт: оплата и возврат того же дня дают ноль; менеджер видит агрегаты, кассир — нет.
    var today = LocalDate.now();
    mvc.perform(
            get("/api/v1/finance/sales")
                .with(staff.role("MANAGER"))
                .param("from", today.minusDays(1).toString())
                .param("to", today.plusDays(1).toString())
                .param("groupBy", "service"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[?(@.label == 'Первичный приём')].refundedMinor").isNotEmpty())
        .andExpect(jsonPath("$.totals.netMinor").isNumber());
    mvc.perform(
            get("/api/v1/finance/sales")
                .with(staff.role("MANAGER"))
                .param("from", today.minusDays(1).toString())
                .param("to", today.plusDays(1).toString())
                .param("groupBy", "weekday"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groupBy").value("weekday"));
    mvc.perform(
            get("/api/v1/finance/sales")
                .with(staff.role("CASHIER"))
                .param("from", today.toString())
                .param("to", today.toString()))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/finance/sales")
                .with(staff.admin())
                .param("from", today.toString())
                .param("to", today.minusDays(1).toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
  }

  private String completedVisitCharge(String startsAt, String endsAt) throws Exception {
    var appointment =
        JsonPath.read(
                mvc.perform(
                        post("/api/v1/appointments")
                            .with(staff.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                """
                                {"patientId":"%s","practitionerId":"%s","startsAt":"%s",
                                 "endsAt":"%s","serviceName":"Первичный приём"}
                                """
                                    .formatted(ANNA, StaffSessions.PETROV, startsAt, endsAt)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(),
                "$.id")
            .toString();
    move(appointment, staff.admin(), "ARRIVED");
    move(appointment, staff.doctor(StaffSessions.PETROV), "COMPLETED");
    var charges =
        mvc.perform(get("/api/v1/billing/charges").with(staff.role("CASHIER")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids =
        JsonPath.read(charges, "$[?(@.appointmentId == '" + appointment + "')].chargeId");
    assertThat(ids).hasSize(1);
    return ids.get(0);
  }

  private void move(String appointment, RequestPostProcessor who, String target) throws Exception {
    mvc.perform(
            post("/api/v1/appointments/" + appointment + "/status")
                .with(who)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + target + "\"}"))
        .andExpect(status().isOk());
  }

  private ResultActions refund(String paymentId, RequestPostProcessor who, String key)
      throws Exception {
    return mvc.perform(
        post("/api/v1/billing/payments/" + paymentId + "/refunds")
            .with(who)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"idempotencyKey\":\"" + key + "\",\"reason\":\"PATIENT_REQUEST\"}"));
  }

  private ResultActions voidCharge(String chargeId, RequestPostProcessor who) throws Exception {
    return mvc.perform(
        post("/api/v1/billing/charges/" + chargeId + "/void")
            .with(who)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\":\"DUPLICATE\"}"));
  }
}
