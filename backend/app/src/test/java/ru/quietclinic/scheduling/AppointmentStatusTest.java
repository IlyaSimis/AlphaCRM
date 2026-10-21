package ru.quietclinic.scheduling;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class AppointmentStatusTest {
  static final String PETROV = "00000000-0000-0000-0000-000000000201";
  static final String LEBEDEVA = "00000000-0000-0000-0000-000000000202";

  @Autowired MockMvc mvc;

  private String book(String practitionerId, String startsAt, String endsAt) throws Exception {
    var body =
        """
        {
          "patientId":"00000000-0000-0000-0000-000000000102",
          "practitionerId":"%s",
          "startsAt":"%s",
          "endsAt":"%s",
          "serviceName":"Повторный приём"
        }
        """
            .formatted(practitionerId, startsAt, endsAt);
    var response =
        mvc.perform(
                post("/api/v1/appointments")
                    .header("X-Demo-Role", "ADMINISTRATOR")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.id");
  }

  private org.springframework.test.web.servlet.ResultActions change(
      String id, String role, String actor, String target) throws Exception {
    return mvc.perform(
        post("/api/v1/appointments/" + id + "/status")
            .header("X-Demo-Role", role)
            .header("X-Actor-Id", actor)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"" + target + "\"}"));
  }

  @Test
  void walksThroughVisitLifecycleWithRoleChecks() throws Exception {
    var id = book(PETROV, "2026-11-03T09:00:00Z", "2026-11-03T09:30:00Z");

    change(id, "DOCTOR", PETROV, "CONFIRMED").andExpect(status().isForbidden());
    change(id, "ADMINISTRATOR", PETROV, "CONFIRMED")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
    change(id, "ADMINISTRATOR", PETROV, "COMPLETED").andExpect(status().isForbidden());
    change(id, "ADMINISTRATOR", PETROV, "ARRIVED").andExpect(status().isOk());
    change(id, "DOCTOR", LEBEDEVA, "IN_PROGRESS").andExpect(status().isForbidden());
    change(id, "DOCTOR", PETROV, "IN_PROGRESS").andExpect(status().isOk());
    change(id, "DOCTOR", PETROV, "COMPLETED")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    change(id, "ADMINISTRATOR", PETROV, "NO_SHOW")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    mvc.perform(
            post("/api/v1/appointments/" + id + "/cancel").header("X-Demo-Role", "ADMINISTRATOR"))
        .andExpect(status().isConflict());
  }

  @Test
  void noShowFreesTheSlotForAnotherBooking() throws Exception {
    var id = book(LEBEDEVA, "2026-11-05T10:00:00Z", "2026-11-05T10:30:00Z");
    change(id, "ADMINISTRATOR", PETROV, "NO_SHOW")
        .andExpect(jsonPath("$.status").value("NO_SHOW"));
    // Неявка освобождает интервал: на него можно записать другого пациента.
    book(LEBEDEVA, "2026-11-05T10:00:00Z", "2026-11-05T10:30:00Z");
  }

  @Test
  void listsPractitionersAndFiltersScheduleByPractitioner() throws Exception {
    mvc.perform(get("/api/v1/practitioners").header("X-Demo-Role", "ADMINISTRATOR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].fullName").value(hasItem("Ольга Лебедева")))
        .andExpect(jsonPath("$[*].fullName").value(hasItem("Александр Петров")));

    mvc.perform(
            get("/api/v1/appointments")
                .param("date", "2026-10-06")
                .param("practitionerId", LEBEDEVA)
                .header("X-Demo-Role", "ADMINISTRATOR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("NO_SHOW"));
  }

  @Test
  void rejectsUnknownPractitioner() throws Exception {
    mvc.perform(
            post("/api/v1/appointments")
                .header("X-Demo-Role", "ADMINISTRATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "patientId":"00000000-0000-0000-0000-000000000102",
                      "practitionerId":"00000000-0000-0000-0000-000000000999",
                      "startsAt":"2026-11-06T10:00:00Z",
                      "endsAt":"2026-11-06T10:30:00Z",
                      "serviceName":"Повторный приём"
                    }
                    """))
        .andExpect(status().isNotFound());
  }
}
