package ru.quietclinic.insights;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.quietclinic.StaffSessions;

/** Персональный дашборд (сценарий D9): виджеты по роли, врач — только свои приёмы. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DashboardControllerTest {
  @Autowired MockMvc mvc;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void doctorSeesOwnMonthAndUnsignedDocumentsButNoMoney() throws Exception {
    mvc.perform(get("/api/v1/me/dashboard").with(staff.doctor(StaffSessions.PETROV)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.monthStart").value("2026-10-01"))
        .andExpect(jsonPath("$.widgets[?(@.code == 'appointments.month')].scope").value("OWN"))
        .andExpect(jsonPath("$.widgets[*].code").value(hasItem("documents.unsigned")))
        .andExpect(jsonPath("$.widgets[*].code").value(not(hasItem("revenue.month"))))
        .andExpect(jsonPath("$.widgets[*].code").value(not(hasItem("charges.unpaid"))));
  }

  @Test
  void cashierSeesUnpaidChargesAndManagerRevenue() throws Exception {
    mvc.perform(get("/api/v1/me/dashboard").with(staff.role("CASHIER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widgets[*].code").value(hasItem("charges.unpaid")))
        .andExpect(jsonPath("$.widgets[*].code").value(not(hasItem("documents.unsigned"))));
    mvc.perform(get("/api/v1/me/dashboard").with(staff.role("MANAGER")))
        .andExpect(jsonPath("$.widgets[*].code").value(hasItem("revenue.month")))
        .andExpect(jsonPath("$.widgets[?(@.code == 'appointments.month')].scope").value("CLINIC"));
  }

  @Test
  void salaryUsesTheCurrentEmployeesOwnMonthlyRate() throws Exception {
    var account = jdbc.queryForMap("select id, clinic_id from staff_user where username = 'admin'");
    var staffId = (java.util.UUID) account.get("id");
    jdbc.update(
        "insert into hr_contract(id, clinic_id, staff_id, position, rate_minor, rate_unit,"
            + " currency, valid_from, valid_to, created_by, created_at) values (?, ?, ?, ?, ?,"
            + " 'MONTH', 'RUB', date '2026-10-01', null, ?, current_timestamp)",
        java.util.UUID.randomUUID(),
        account.get("clinic_id"),
        staffId,
        "Administrator",
        7_500_000L,
        staffId);

    mvc.perform(get("/api/v1/me/dashboard").with(staff.role("OWNER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widgets[?(@.code == 'salary.month')].scope").value("OWN"))
        .andExpect(jsonPath("$.widgets[?(@.code == 'salary.month')].value").value(7_500_000));
  }

  @Test
  void supportSeesPlatformWidgetsOnly() throws Exception {
    mvc.perform(get("/api/v1/me/dashboard").with(staff.role("SUPPORT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widgets[*].code").value(hasItem("delivery.failed")))
        .andExpect(jsonPath("$.widgets[*].code").value(hasItem("events.backlog")))
        .andExpect(jsonPath("$.widgets[*].code").value(not(hasItem("charges.unpaid"))));
  }
}
