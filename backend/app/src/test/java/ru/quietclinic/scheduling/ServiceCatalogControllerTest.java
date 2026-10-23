package ru.quietclinic.scheduling;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
import ru.quietclinic.SecondClinic;
import ru.quietclinic.StaffSessions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ServiceCatalogControllerTest {
  static final String REPEAT = "00000000-0000-0000-0000-000000000602";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void listsServicesWithDurationAndPrice() throws Exception {
    mvc.perform(get("/api/v1/services").with(staff.role("CASHIER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].name").value("Первичный приём"))
        .andExpect(jsonPath("$[0].durationMinutes").value(50))
        .andExpect(jsonPath("$[0].priceMinor").value(450000))
        .andExpect(jsonPath("$[1].name").value("Повторный приём"))
        .andExpect(jsonPath("$[1].durationMinutes").value(30))
        .andExpect(jsonPath("$[1].priceMinor").value(300000))
        .andExpect(jsonPath("$[1].currency").value("RUB"))
        .andExpect(jsonPath("$[1].active").value(true));
  }

  @Test
  void newPriceDoesNotRewriteExistingAppointments() throws Exception {
    var repeat = "00000000-0000-0000-0000-000000000602";
    var booking =
        """
        {"patientId":"00000000-0000-0000-0000-000000000101",
         "practitionerId":"00000000-0000-0000-0000-000000000201",
         "startsAt":"%s","endsAt":"%s","serviceName":"Повторный приём"}
        """;
    var before =
        mvc.perform(
                post("/api/v1/appointments")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(booking.formatted("2026-12-15T09:00:00Z", "2026-12-15T09:30:00Z")))
            .andExpect(jsonPath("$.amountMinor").value(300000))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String oldId = JsonPath.read(before, "$.id");
    try {
      mvc.perform(
              put("/api/v1/services/" + repeat + "/price")
                  .with(staff.role("CASHIER"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"priceMinor\":350000}"))
          .andExpect(status().isForbidden());
      mvc.perform(
              put("/api/v1/services/" + repeat + "/price")
                  .with(staff.admin())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"priceMinor\":350000}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.priceMinor").value(350000));

      mvc.perform(
              post("/api/v1/appointments")
                  .with(staff.admin())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(booking.formatted("2026-12-15T10:00:00Z", "2026-12-15T10:30:00Z")))
          .andExpect(jsonPath("$.amountMinor").value(350000));
      mvc.perform(get("/api/v1/appointments").param("date", "2026-12-15").with(staff.admin()))
          .andExpect(jsonPath("$[?(@.id == '" + oldId + "')].amountMinor").value(300000));
    } finally {
      mvc.perform(
          put("/api/v1/services/" + repeat + "/price")
              .with(staff.admin())
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"priceMinor\":300000}"));
    }
  }

  @Test
  void administratorCreatesAndSwitchesOffServiceWithoutTouchingOldAppointments() throws Exception {
    // В H2 название услуги уникально во всей базе: у каждого прогона своё.
    var name = "Group session " + UUID.randomUUID().toString().substring(0, 8);
    var body =
        """
        {"name":"%s","durationMinutes":90,"priceMinor":250000,"currency":"RUB"}
        """
            .formatted(name);
    mvc.perform(
            post("/api/v1/services")
                .with(staff.role("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    var created =
        mvc.perform(
                post("/api/v1/services")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.durationMinutes").value(90))
            .andExpect(jsonPath("$.priceMinor").value(250000))
            .andExpect(jsonPath("$.currency").value("RUB"))
            .andExpect(jsonPath("$.active").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(created, "$.id");
    try {
      mvc.perform(
              post("/api/v1/services")
                  .with(staff.admin())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("SERVICE_NAME_TAKEN"));
      mvc.perform(
              post("/api/v1/services")
                  .with(staff.admin())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"name":"Bad","durationMinutes":0,"priceMinor":-1,"currency":"USD"}
                      """))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
      // Новая услуга сразу доступна в записи.
      mvc.perform(get("/api/v1/services").with(staff.doctor(StaffSessions.PETROV)))
          .andExpect(jsonPath("$[*].name").value(hasItem(name)));
      var booked =
          mvc.perform(
                  post("/api/v1/appointments")
                      .with(staff.admin())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(booking(name, "2026-12-17T09:00:00Z", "2026-12-17T10:30:00Z")))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.serviceName").value(name))
              .andExpect(jsonPath("$.amountMinor").value(250000))
              .andReturn()
              .getResponse()
              .getContentAsString();
      String appointment = JsonPath.read(booked, "$.id");

      mvc.perform(post("/api/v1/services/" + id + "/deactivate").with(staff.role("MANAGER")))
          .andExpect(status().isForbidden());
      mvc.perform(post("/api/v1/services/" + id + "/deactivate").with(staff.admin()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.active").value(false));
      mvc.perform(get("/api/v1/services").with(staff.admin()))
          .andExpect(jsonPath("$[*].name").value(not(hasItem(name))));
      mvc.perform(get("/api/v1/services").param("all", "true").with(staff.admin()))
          .andExpect(jsonPath("$[?(@.id == '" + id + "')].active").value(false));
      // Не администратору выключенные не показываются и с all=true.
      mvc.perform(get("/api/v1/services").param("all", "true").with(staff.role("CASHIER")))
          .andExpect(jsonPath("$[*].name").value(not(hasItem(name))));
      mvc.perform(
              post("/api/v1/appointments")
                  .with(staff.admin())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(booking(name, "2026-12-17T11:00:00Z", "2026-12-17T12:30:00Z")))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("SERVICE_INACTIVE"));
      // Старая запись хранит снимок названия и цены выключенной услуги.
      mvc.perform(get("/api/v1/appointments").param("date", "2026-12-17").with(staff.admin()))
          .andExpect(jsonPath("$[?(@.id == '" + appointment + "')].serviceName").value(name))
          .andExpect(jsonPath("$[?(@.id == '" + appointment + "')].amountMinor").value(250000));

      mvc.perform(post("/api/v1/services/" + id + "/activate").with(staff.admin()))
          .andExpect(jsonPath("$.active").value(true));
      mvc.perform(get("/api/v1/services").with(staff.role("CASHIER")))
          .andExpect(jsonPath("$[*].name").value(hasItem(name)));
    } finally {
      mvc.perform(post("/api/v1/services/" + id + "/deactivate").with(staff.admin()));
    }
  }

  @Test
  void servicesBelongToTheirClinic() throws Exception {
    SecondClinic.ensure(jdbc);
    var other = staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    mvc.perform(get("/api/v1/services").with(other))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id").value(not(hasItem(REPEAT))));
    mvc.perform(post("/api/v1/services/" + REPEAT + "/deactivate").with(other))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
    mvc.perform(
            put("/api/v1/services/" + REPEAT + "/price")
                .with(other)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priceMinor\":1}"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/services").with(staff.admin()))
        .andExpect(jsonPath("$[?(@.id == '" + REPEAT + "')].priceMinor").value(300000));
  }

  private static String booking(String service, String startsAt, String endsAt) {
    return """
        {"patientId":"00000000-0000-0000-0000-000000000102",
         "practitionerId":"00000000-0000-0000-0000-000000000201",
         "startsAt":"%s","endsAt":"%s","serviceName":"%s"}
        """
        .formatted(startsAt, endsAt, service);
  }
}
