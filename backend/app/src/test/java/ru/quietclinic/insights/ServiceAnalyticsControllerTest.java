package ru.quietclinic.insights;

import static org.hamcrest.Matchers.hasItem;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ServiceAnalyticsControllerTest {
  private static final String PRIMARY_SERVICE = "00000000-0000-0000-0000-000000000601";

  @Autowired MockMvc mvc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void returnsServiceAttendanceFinanceAndPreviousPeriod() throws Exception {
    mvc.perform(
            get("/api/v1/analytics/services")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.previousFrom").value("2026-08-31"))
        .andExpect(jsonPath("$.previousTo").value("2026-09-30"))
        .andExpect(jsonPath("$.totals.appointments").isNumber())
        .andExpect(jsonPath("$.totals.visits").isNumber())
        .andExpect(jsonPath("$.totals.uniquePatients").isNumber())
        .andExpect(jsonPath("$.totals.revenueMinor").isNumber())
        .andExpect(jsonPath("$.rows[*].serviceName").value(hasItem("Первичный приём")))
        .andExpect(jsonPath("$.rows[0].previousVisits").isNumber())
        .andExpect(jsonPath("$.rows[0].previousRevenueMinor").isNumber());
  }

  @Test
  void opensSourceAppointmentsForServiceAndChecksAccess() throws Exception {
    mvc.perform(
            get("/api/v1/analytics/services/appointments")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .param("serviceKey", PRIMARY_SERVICE)
                .with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serviceName").value("Первичный приём"))
        .andExpect(jsonPath("$.appointments").isArray())
        .andExpect(jsonPath("$.appointments[*].patientName").value(hasItem("Анна Михайлова")))
        .andExpect(jsonPath("$.appointments[0].complaints").doesNotExist());

    mvc.perform(
            get("/api/v1/analytics/services")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .with(staff.role("DOCTOR")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/analytics/services")
                .param("from", "2026-10-31")
                .param("to", "2026-10-01")
                .with(staff.role("MANAGER")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
  }
}
