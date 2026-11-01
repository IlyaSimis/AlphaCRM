package ru.quietclinic.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.quietclinic.SecondClinic;
import ru.quietclinic.StaffSessions;

/** Задачи регистратуры (свободный текст, пациент по желанию) и лист ожидания. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class RegistryControllerTest {
  static final String MARIA = "00000000-0000-0000-0000-000000000103";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  /** Тестовые кассир и менеджер: их заводит администратор через API сотрудников. */
  private String cashier() {
    return staff.userId("t.cashier");
  }

  private String manager() {
    return staff.userId("t.manager");
  }

  private ResultActions createTask(String title, String dueDate, String assignee, String patient)
      throws Exception {
    String patientField = patient == null ? "" : ",\"patientId\":\"" + patient + "\"";
    return mvc.perform(
        post("/api/v1/registry/tasks")
            .with(staff.admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"title\":\"%s\",\"dueDate\":\"%s\",\"assigneeId\":\"%s\"%s}"
                    .formatted(title, dueDate, assignee, patientField)));
  }

  @Test
  void tasksForTodayAndPatientAreClosedByAssigneeOrAdministrator() throws Exception {
    String created =
        createTask("Call back about the referral", "2026-10-21", cashier(), MARIA)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.title").value("Call back about the referral"))
            .andExpect(jsonPath("$.patientId").value(MARIA))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(created, "$.id");
    createTask(" ", "2026-10-21", cashier(), MARIA)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    createTask("x".repeat(201), "2026-10-21", cashier(), MARIA).andExpect(status().isBadRequest());

    mvc.perform(get("/api/v1/registry/tasks").param("dueTo", "2026-10-21").with(staff.admin()))
        .andExpect(jsonPath("$[*].id").value(hasItem(id)));
    mvc.perform(get("/api/v1/registry/tasks").param("dueTo", "2026-10-20").with(staff.admin()))
        .andExpect(jsonPath("$[*].id").value(not(hasItem(id))));
    mvc.perform(get("/api/v1/registry/tasks").param("patientId", MARIA).with(staff.admin()))
        .andExpect(jsonPath("$[*].id").value(hasItem(id)));

    mvc.perform(post("/api/v1/registry/tasks/" + id + "/done").with(staff.role("MANAGER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("TASK_CLOSE_FORBIDDEN"));
    mvc.perform(post("/api/v1/registry/tasks/" + id + "/done").with(staff.role("CASHIER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"));
    mvc.perform(get("/api/v1/registry/tasks").param("dueTo", "2026-10-21").with(staff.admin()))
        .andExpect(jsonPath("$[*].id").value(not(hasItem(id))));
    mvc.perform(post("/api/v1/registry/tasks/" + id + "/reopen").with(staff.admin()))
        .andExpect(jsonPath("$.status").value("OPEN"));
  }

  @Test
  void taskWithoutPatientIsInTodayListButNotInPatientCard() throws Exception {
    String created =
        createTask("Order printer paper", "2026-10-22", manager(), null)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.patientId").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(created, "$.id");
    mvc.perform(get("/api/v1/registry/tasks").param("dueTo", "2026-10-22").with(staff.admin()))
        .andExpect(jsonPath("$[?(@.id == '" + id + "')].title").value("Order printer paper"));
    mvc.perform(get("/api/v1/registry/tasks").param("patientId", MARIA).with(staff.admin()))
        .andExpect(jsonPath("$[*].id").value(not(hasItem(id))));
    createTask("Call back", "2026-10-22", manager(), "00000000-0000-0000-0000-000000009999")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"));

    // Задачу без пациента закрывает её исполнитель; другая клиника её не видит.
    SecondClinic.ensure(jdbc);
    StaffSessions.Login other =
        staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    mvc.perform(get("/api/v1/registry/tasks").param("dueTo", "2026-10-22").with(other))
        .andExpect(jsonPath("$[*].id").value(not(hasItem(id))));
    mvc.perform(post("/api/v1/registry/tasks/" + id + "/done").with(other))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    mvc.perform(post("/api/v1/registry/tasks/" + id + "/done").with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"));
  }

  @Test
  void auditKeepsOnlyWhetherTaskHasPatientNotItsText() throws Exception {
    createTask("Secret task text", "2026-10-23", cashier(), null).andExpect(status().isCreated());
    List<String> reasons =
        jdbc.queryForList(
            "select reason from audit_event where action = 'REGISTRY_TASK_CREATED'", String.class);
    assertThat(reasons)
        .contains("without-patient")
        .noneMatch(r -> r != null && r.contains("Secret"));
  }

  @Test
  void taskRequiresActiveAssigneeAndAdministrator() throws Exception {
    createTask("Update consent", "2026-10-21", "00000000-0000-0000-0000-000000009999", MARIA)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_ASSIGNEE"));
    mvc.perform(
            post("/api/v1/registry/tasks")
                .with(staff.role("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"Call back","dueDate":"2026-10-21","assigneeId":"%s"}
                    """
                        .formatted(manager())))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/registry/tasks").with(staff.doctor(StaffSessions.PETROV)))
        .andExpect(status().isForbidden());
  }

  @Test
  void waitlistMatchesFreedSlotAndIsClosedByRegularAppointment() throws Exception {
    String created =
        mvc.perform(
                post("/api/v1/registry/waitlist")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"patientId":"%s","practitionerId":"00000000-0000-0000-0000-000000000202",
                         "serviceId":"00000000-0000-0000-0000-000000000602",
                         "wishFrom":"2026-12-01","wishTo":"2026-12-10"}
                        """
                            .formatted(MARIA)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String entry = JsonPath.read(created, "$.id");
    MockHttpServletRequestBuilder matches =
        get("/api/v1/registry/waitlist").param("practitionerId", StaffSessions.LEBEDEVA);

    mvc.perform(matches.param("date", "2026-12-03").with(staff.admin()))
        .andExpect(jsonPath("$[*].id").value(hasItem(entry)));
    mvc.perform(
            get("/api/v1/registry/waitlist")
                .param("practitionerId", StaffSessions.LEBEDEVA)
                .param("date", "2026-12-17")
                .with(staff.admin()))
        .andExpect(jsonPath("$[*].id").value(not(hasItem(entry))));

    // Запись — обычным POST /appointments, затем ожидание закрывается ссылкой на неё.
    String booked =
        mvc.perform(
                post("/api/v1/appointments")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"patientId":"%s","practitionerId":"%s","startsAt":"2026-12-03T12:00:00Z",
                         "endsAt":"2026-12-03T12:30:00Z","serviceName":"Повторный приём"}
                        """
                            .formatted(MARIA, StaffSessions.LEBEDEVA)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String appointment = JsonPath.read(booked, "$.id");
    mvc.perform(
            post("/api/v1/registry/waitlist/" + entry + "/book")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"appointmentId\":\"" + appointment + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BOOKED"))
        .andExpect(jsonPath("$.appointmentId").value(appointment));
    mvc.perform(post("/api/v1/registry/waitlist/" + entry + "/cancel").with(staff.admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WAITLIST_CLOSED"));
  }

  @Test
  void waitlistRejectsInvertedPeriod() throws Exception {
    mvc.perform(
            post("/api/v1/registry/waitlist")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"%s","practitionerId":"%s",
                     "serviceId":"00000000-0000-0000-0000-000000000602",
                     "wishFrom":"2026-12-10","wishTo":"2026-12-01"}
                    """
                        .formatted(MARIA, StaffSessions.PETROV)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
  }
}
