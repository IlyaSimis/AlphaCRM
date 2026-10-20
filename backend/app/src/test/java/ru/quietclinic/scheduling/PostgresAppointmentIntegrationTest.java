package ru.quietclinic.scheduling;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PostgresAppointmentIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:18-alpine")
          .withDatabaseName("quiet_clinic")
          .withUsername("quiet_clinic")
          .withPassword("quiet_clinic");

  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired MockMvc mvc;

  @Test
  void rejectsOverlappingAppointment() throws Exception {
    var first =
        """
        {
          "patientId":"00000000-0000-0000-0000-000000000101",
          "practitionerId":"00000000-0000-0000-0000-000000000201",
          "startsAt":"2026-10-21T09:00:00Z",
          "endsAt":"2026-10-21T09:50:00Z",
          "serviceName":"Первичный приём"
        }
        """;
    var overlap = first.replace("09:00:00Z", "09:20:00Z").replace("09:50:00Z", "10:00:00Z");

    mvc.perform(
            post("/api/v1/appointments")
                .header("X-Demo-Role", "REGISTRAR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(first))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v1/appointments")
                .header("X-Demo-Role", "REGISTRAR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(overlap))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_ALREADY_BOOKED"));
  }
}
