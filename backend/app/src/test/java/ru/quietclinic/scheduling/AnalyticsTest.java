package ru.quietclinic.scheduling;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AnalyticsTest {
  @Autowired MockMvc mvc;

  @Test
  void aggregatesSeededHistoryByDayPractitionerAndStatus() throws Exception {
    mvc.perform(
            get("/api/v1/analytics/appointments")
                .param("from", "2026-10-06")
                .param("to", "2026-10-06")
                .header("X-Demo-Role", "MANAGER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows.length()").value(2))
        .andExpect(
            jsonPath(
                    "$.rows[?(@.status == 'COMPLETED' && @.practitionerId == "
                        + "'00000000-0000-0000-0000-000000000201')].count")
                .value(2))
        .andExpect(jsonPath("$.rows[?(@.status == 'NO_SHOW')].count").value(1));
  }

  @Test
  void restrictsAccessAndValidatesRange() throws Exception {
    mvc.perform(
            get("/api/v1/analytics/appointments")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .header("X-Demo-Role", "DOCTOR"))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/analytics/appointments")
                .param("from", "2026-10-31")
                .param("to", "2026-10-01")
                .header("X-Demo-Role", "MANAGER"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
  }
}
