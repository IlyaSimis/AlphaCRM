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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
/** HTTP-сценарии расписания: права ролей, переходы статусов, отмена и повторная запись. */
class SchedulingControllerTest {
  static final String PETROV = "00000000-0000-0000-0000-000000000201";
  static final String LEBEDEVA = "00000000-0000-0000-0000-000000000202";

  @Autowired MockMvc mvc;

  static final String DMITRY = "00000000-0000-0000-0000-000000000102";

  private String book(String practitionerId, String startsAt, String endsAt) throws Exception {
    return book(DMITRY, practitionerId, startsAt, endsAt);
  }

  private String book(String patientId, String practitionerId, String startsAt, String endsAt)
      throws Exception {
    var response =
        attemptBooking(patientId, practitionerId, startsAt, endsAt, "Повторный приём")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.id");
  }

  private ResultActions attemptBooking(
      String patientId, String practitionerId, String startsAt, String endsAt, String service)
      throws Exception {
    var body =
        """
        {"patientId":"%s","practitionerId":"%s","startsAt":"%s","endsAt":"%s","serviceName":"%s"}
        """
            .formatted(patientId, practitionerId, startsAt, endsAt, service);
    return mvc.perform(
        post("/api/v1/appointments")
            .header("X-Demo-Role", "ADMINISTRATOR")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private ResultActions reschedule(
      String id, String role, String practitionerId, String startsAt, String endsAt)
      throws Exception {
    return mvc.perform(
        post("/api/v1/appointments/" + id + "/reschedule")
            .header("X-Demo-Role", role)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"practitionerId":"%s","startsAt":"%s","endsAt":"%s"}
                """
                    .formatted(practitionerId, startsAt, endsAt)));
  }

  private ResultActions change(
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

    // Завершение приёма создаёт один счёт на снимок цены записи.
    mvc.perform(get("/api/v1/billing/charges").header("X-Demo-Role", "CASHIER"))
        .andExpect(jsonPath("$[?(@.appointmentId == '" + id + "')].amountMinor").value(300000))
        .andExpect(jsonPath("$[?(@.appointmentId == '" + id + "')].status").value("PENDING"));

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

  @Test
  void keepsPriceSnapshotAndRejectsUnknownService() throws Exception {
    attemptBooking(
            DMITRY, PETROV, "2026-11-03T11:00:00Z", "2026-11-03T11:50:00Z",
            "Первичный приём")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amountMinor").value(450000))
        .andExpect(jsonPath("$.currency").value("RUB"));
    attemptBooking(DMITRY, PETROV, "2026-11-03T13:00:00Z", "2026-11-03T13:30:00Z", "Массаж")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("UNKNOWN_SERVICE"));
  }

  @Test
  void rejectsBookingOutsideWorkingHours() throws Exception {
    // Среда: у Петрова нет смены.
    attemptBooking(
            DMITRY, PETROV, "2026-11-04T10:00:00Z", "2026-11-04T10:30:00Z",
            "Повторный приём")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OUTSIDE_WORKING_HOURS"));
    // Вторник, но конец приёма после 18:00.
    attemptBooking(
            DMITRY, PETROV, "2026-11-03T17:45:00Z", "2026-11-03T18:15:00Z",
            "Повторный приём")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OUTSIDE_WORKING_HOURS"));
  }

  @Test
  void administratorReschedulesWithinHoursWithoutOverlap() throws Exception {
    var id = book(PETROV, "2026-11-10T09:00:00Z", "2026-11-10T09:30:00Z");
    book(LEBEDEVA, "2026-11-12T11:00:00Z", "2026-11-12T11:30:00Z");

    reschedule(id, "DOCTOR", LEBEDEVA, "2026-11-12T10:00:00Z", "2026-11-12T10:30:00Z")
        .andExpect(status().isForbidden());
    reschedule(id, "ADMINISTRATOR", LEBEDEVA, "2026-11-12T10:00:00Z", "2026-11-12T10:30:00Z")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.practitionerId").value(LEBEDEVA))
        .andExpect(jsonPath("$.startsAt").value("2026-11-12T10:00:00Z"))
        .andExpect(jsonPath("$.status").value("BOOKED"));
    // Сдвиг внутри собственного интервала — не пересечение с самой собой.
    reschedule(id, "ADMINISTRATOR", LEBEDEVA, "2026-11-12T10:15:00Z", "2026-11-12T10:45:00Z")
        .andExpect(status().isOk());
    reschedule(id, "ADMINISTRATOR", LEBEDEVA, "2026-11-12T10:50:00Z", "2026-11-12T11:20:00Z")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_ALREADY_BOOKED"));
    reschedule(id, "ADMINISTRATOR", LEBEDEVA, "2026-11-12T18:45:00Z", "2026-11-12T19:15:00Z")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OUTSIDE_WORKING_HOURS"));
    reschedule(id, "ADMINISTRATOR", LEBEDEVA, "2026-11-12T10:30:00Z", "2026-11-12T10:30:00Z")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_INTERVAL"));

    mvc.perform(get("/api/v1/audit").header("X-Demo-Role", "MANAGER"))
        .andExpect(
            jsonPath("$[?(@.action == 'APPOINTMENT_RESCHEDULED')].reason")
                .value(hasItem("2026-11-10T09:00Z>2026-11-12T10:00Z;practitioner")));
  }

  @Test
  void finishedAppointmentsCannotBeRescheduled() throws Exception {
    var id = book(PETROV, "2026-11-10T11:00:00Z", "2026-11-10T11:30:00Z");
    change(id, "ADMINISTRATOR", PETROV, "NO_SHOW").andExpect(status().isOk());
    reschedule(id, "ADMINISTRATOR", PETROV, "2026-11-10T12:00:00Z", "2026-11-10T12:30:00Z")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
  }

  @Test
  void returnsPatientHistoryForRangeWithoutBreakingDayQuery() throws Exception {
    mvc.perform(
            get("/api/v1/appointments")
                .param("patientId", "00000000-0000-0000-0000-000000000101")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .header("X-Demo-Role", "ADMINISTRATOR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[0].startsAt").value("2026-10-06T09:00:00Z"));
    mvc.perform(
            get("/api/v1/appointments")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .param("patientId", "00000000-0000-0000-0000-000000000101")
                .param("status", "COMPLETED")
                .header("X-Demo-Role", "CASHIER"))
        .andExpect(jsonPath("$.length()").value(4));
    mvc.perform(get("/api/v1/appointments").header("X-Demo-Role", "ADMINISTRATOR"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
  }

  @Test
  void bookingGrantsCareRelationshipToTheDoctor() throws Exception {
    var created =
        mvc.perform(
                post("/api/v1/patients")
                    .header("X-Demo-Role", "ADMINISTRATOR")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fullName":"Новый Пациент","birthDate":"1990-01-01","phone":"+70000000000",
                         "consentVersion":"v1.2","consentBasis":"SIGNED_FORM"}
                        """))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String patientId = JsonPath.read(created, "$.id");
    var history = "/api/v1/clinical/patients/" + patientId + "/encounters";

    mvc.perform(get(history).header("X-Demo-Role", "DOCTOR").header("X-Actor-Id", LEBEDEVA))
        .andExpect(status().isForbidden());
    book(patientId, LEBEDEVA, "2026-11-19T10:00:00Z", "2026-11-19T10:30:00Z");
    mvc.perform(get(history).header("X-Demo-Role", "DOCTOR").header("X-Actor-Id", LEBEDEVA))
        .andExpect(status().isOk());
  }
}
