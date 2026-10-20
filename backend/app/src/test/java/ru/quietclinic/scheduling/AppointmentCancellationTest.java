package ru.quietclinic.scheduling;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AppointmentCancellationTest {
  @Autowired MockMvc mvc;

  @Test
  void onlyAdministratorCanCancelAndTheSlotCanBeBookedAgain() throws Exception {
    mvc.perform(
            post("/api/v1/appointments/00000000-0000-0000-0000-000000000301/cancel")
                .header("X-Demo-Role", "DOCTOR"))
        .andExpect(status().isForbidden());

    mvc.perform(
            post("/api/v1/appointments/00000000-0000-0000-0000-000000000301/cancel")
                .header("X-Demo-Role", "ADMINISTRATOR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    mvc.perform(
            get("/api/v1/appointments").param("date", "2026-10-20").header("X-Demo-Role", "DOCTOR"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.status == 'CANCELLED')].id")
                .value(hasItem("00000000-0000-0000-0000-000000000301")));

    mvc.perform(
            post("/api/v1/appointments/00000000-0000-0000-0000-000000000301/cancel")
                .header("X-Demo-Role", "ADMINISTRATOR"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_CANCELLED"));

    var rebook =
        """
        {
          "patientId":"00000000-0000-0000-0000-000000000103",
          "practitionerId":"00000000-0000-0000-0000-000000000201",
          "startsAt":"2026-10-20T09:00:00Z",
          "endsAt":"2026-10-20T09:50:00Z",
          "serviceName":"Повторный приём"
        }
        """;
    mvc.perform(
            post("/api/v1/appointments")
                .header("X-Demo-Role", "ADMINISTRATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(rebook))
        .andExpect(status().isCreated());
  }
}
