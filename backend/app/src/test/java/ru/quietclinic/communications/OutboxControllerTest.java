package ru.quietclinic.communications;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.quietclinic.SecondClinic;
import ru.quietclinic.StaffSessions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OutboxControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void administratorSeesTemplateChannelAndStatusWithoutTextOrContact() throws Exception {
    var body =
        mvc.perform(
                post("/api/v1/appointments")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"patientId":"00000000-0000-0000-0000-000000000101",
                         "practitionerId":"%s","startsAt":"2027-03-02T09:00:00Z",
                         "endsAt":"2027-03-02T09:30:00Z","serviceName":"Повторный приём"}
                        """
                            .formatted(StaffSessions.PETROV)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String appointment = JsonPath.read(body, "$.id");

    mvc.perform(get("/api/v1/outbox").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].aggregateId").value(hasItem(appointment)))
        .andExpect(
            jsonPath("$[?(@.aggregateId == '" + appointment + "')].template")
                .value(
                    Matchers.containsInAnyOrder(
                        "appointment-confirmed", "appointment-reminder")))
        .andExpect(jsonPath("$[0].text").doesNotExist())
        .andExpect(jsonPath("$[0].payload").doesNotExist())
        .andExpect(content().string(Matchers.not(Matchers.containsString("+7916"))));

    mvc.perform(get("/api/v1/outbox").with(staff.role("CASHIER")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/outbox").with(staff.doctor(StaffSessions.PETROV)))
        .andExpect(status().isForbidden());

    SecondClinic.ensure(jdbc);
    var other = staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    mvc.perform(get("/api/v1/outbox").with(other))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].aggregateId").value(Matchers.not(hasItem(appointment))));
  }
}
