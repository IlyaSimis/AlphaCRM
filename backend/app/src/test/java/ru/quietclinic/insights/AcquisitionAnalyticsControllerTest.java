package ru.quietclinic.insights;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.StaffSessions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AcquisitionAnalyticsControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void setUp() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void returnsReferralCohortWithoutPatientIdentifiersAndSeparateWebState() throws Exception {
    UUID patient = UUID.randomUUID();
    UUID appointment = UUID.randomUUID();
    jdbc.update(
        "insert into patient(id, clinic_id, full_name, birth_date, created_at, referral_source)"
            + " values (?, '00000000-0000-0000-0000-00000000c001', 'Acquisition Test',"
            + " DATE '1990-01-01', timestamp with time zone '2026-10-20 12:00:00+00', 'WEBSITE')",
        patient);
    jdbc.update(
        "insert into appointment(id, patient_id, practitioner_id, starts_at, ends_at, service_name,"
            + " status, created_at, version) values (?, ?, ?,"
            + " timestamp with time zone '2026-10-20 15:00:00+00',"
            + " timestamp with time zone '2026-10-20 15:30:00+00',"
            + " 'Повторный приём', 'COMPLETED', CURRENT_TIMESTAMP, 0)",
        appointment,
        patient,
        UUID.fromString(StaffSessions.PETROV));

    mvc.perform(
            get("/api/v1/analytics/acquisition")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.referralSources[?(@.source == 'WEBSITE')].contacts").value(hasItem(1)))
        .andExpect(jsonPath("$.referralSources[?(@.source == 'WEBSITE')].booked").value(hasItem(1)))
        .andExpect(jsonPath("$.referralSources[?(@.source == 'WEBSITE')].visited").value(hasItem(1)))
        .andExpect(jsonPath("$.web.state").value("NOT_CONFIGURED"))
        .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Acquisition Test"))))
        .andExpect(jsonPath("$.referralSources[0].patientId").doesNotExist());
  }

  @Test
  void rejectsRolesWithoutAnalytics() throws Exception {
    mvc.perform(
            get("/api/v1/analytics/acquisition")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .with(staff.role("SUPPORT")))
        .andExpect(status().isForbidden());
  }
}
