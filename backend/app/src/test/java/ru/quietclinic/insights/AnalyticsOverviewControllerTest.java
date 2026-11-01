package ru.quietclinic.insights;

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
class AnalyticsOverviewControllerTest {
  @Autowired MockMvc mvc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void returnsWeeklyFinancialPatientAndOperationalAggregates() throws Exception {
    mvc.perform(
            get("/api/v1/analytics/overview")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totals.appointments").isNumber())
        .andExpect(jsonPath("$.totals.revenueMinor").isNumber())
        .andExpect(jsonPath("$.totals.occupiedMinutes").isNumber())
        .andExpect(jsonPath("$.weeks.length()").value(5))
        .andExpect(jsonPath("$.practitioners").isArray())
        .andExpect(jsonPath("$.rooms").isArray())
        .andExpect(jsonPath("$.funnel.contacts").isNumber())
        .andExpect(jsonPath("$.queues.unpaidCharges").isNumber())
        .andExpect(jsonPath("$.totals.patientId").doesNotExist())
        .andExpect(jsonPath("$.funnel.patientName").doesNotExist());
  }

  @Test
  void filtersByDoctorAndRejectsRolesWithoutAnalytics() throws Exception {
    mvc.perform(
            get("/api/v1/analytics/overview")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .param("practitionerId", StaffSessions.PETROV)
                .with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.practitioners.length()").value(1))
        .andExpect(jsonPath("$.practitioners[0].id").value(StaffSessions.PETROV));
    mvc.perform(
            get("/api/v1/analytics/overview")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .with(staff.role("SUPPORT")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/analytics/overview")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .with(staff.role("DOCTOR")))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/analytics/overview")
                .param("from", "2026-10-31")
                .param("to", "2026-10-01")
                .with(staff.role("MANAGER")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
  }
}
