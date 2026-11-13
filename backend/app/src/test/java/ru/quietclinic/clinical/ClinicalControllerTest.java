package ru.quietclinic.clinical;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.quietclinic.StaffSessions;

/** Приём по записи: старт, черновик, подпись из заметки, дополнение и история с документами. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ClinicalControllerTest {
  static final String PETROV = "00000000-0000-0000-0000-000000000201";
  static final String LEBEDEVA = "00000000-0000-0000-0000-000000000202";
  static final String DMITRY = "00000000-0000-0000-0000-000000000102";
  static final String ANNA = "00000000-0000-0000-0000-000000000101";
  static final String TEMPLATE = "{\"templateVersion\":\"v2.4\"}";

  @Autowired MockMvc mvc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  private String arrivedAppointment(String startsAt, String endsAt) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/appointments")
                    .with(staff.role("ADMINISTRATOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"patientId":"%s","practitionerId":"%s","startsAt":"%s","endsAt":"%s",
                         "serviceName":"Повторный приём"}
                        """
                            .formatted(DMITRY, PETROV, startsAt, endsAt)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(body, "$.id");
    mvc.perform(
            post("/api/v1/appointments/" + id + "/status")
                .with(staff.role("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ARRIVED\"}"))
        .andExpect(status().isOk());
    return id;
  }

  private ResultActions start(String appointmentId, String patientId, String actor)
      throws Exception {
    return mvc.perform(
        post("/api/v1/clinical/encounters")
            .with(staff.doctor(actor))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"appointmentId":"%s","patientId":"%s","practitionerId":"%s"}
                """
                    .formatted(appointmentId, patientId, actor)));
  }

  private ResultActions doctor(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(staff.doctor(PETROV)));
  }

  @Test
  void startsOnlyFromOwnArrivedAppointmentAndReusesEncounter() throws Exception {
    String appointment = arrivedAppointment("2026-11-17T09:00:00Z", "2026-11-17T09:30:00Z");

    start(appointment, DMITRY, LEBEDEVA)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_MISMATCH"));
    start(appointment, ANNA, PETROV)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_MISMATCH"));
    start("00000000-0000-0000-0000-000000009999", DMITRY, PETROV).andExpect(status().isNotFound());

    String first =
        start(appointment, DMITRY, PETROV)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.appointmentId").value(appointment))
            .andExpect(jsonPath("$.complaints").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String encounterId = JsonPath.read(first, "$.encounterId");
    start(appointment, DMITRY, PETROV)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.encounterId").value(encounterId));
  }

  private String bookedAppointment(String startsAt, String endsAt) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/appointments")
                    .with(staff.role("ADMINISTRATOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"patientId":"%s","practitionerId":"%s","startsAt":"%s","endsAt":"%s",
                         "serviceName":"Повторный приём"}
                        """
                            .formatted(DMITRY, PETROV, startsAt, endsAt)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private ResultActions setStatus(String appointmentId, String target, String role)
      throws Exception {
    return mvc.perform(
        post("/api/v1/appointments/" + appointmentId + "/status")
            .with("DOCTOR".equals(role) ? staff.doctor(PETROV) : staff.role(role))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"" + target + "\"}"));
  }

  private ResultActions day(String date) throws Exception {
    return mvc.perform(get("/api/v1/appointments").param("date", date).with(staff.doctor(PETROV)));
  }

  @Test
  void doctorStartsSeveralVisitsInARowFromBookedOrConfirmed() throws Exception {
    // Свой день (четверг): 24.11 уже занимают записи BillingControllerTest, база H2 общая.
    String booked = bookedAppointment("2026-11-26T09:00:00Z", "2026-11-26T09:30:00Z");
    String confirmed = bookedAppointment("2026-11-26T10:00:00Z", "2026-11-26T10:30:00Z");
    setStatus(confirmed, "CONFIRMED", "ADMINISTRATOR").andExpect(status().isOk());

    // Старт из «Ожидает» и «Подтверждён» переводит запись в IN_PROGRESS.
    start(booked, DMITRY, PETROV).andExpect(status().isCreated());
    // Второй пациент — не дожидаясь завершения и подписи первого.
    String second =
        start(confirmed, DMITRY, PETROV)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    day("2026-11-26")
        .andExpect(jsonPath("$[?(@.id == '" + booked + "')].status").value(contains("IN_PROGRESS")))
        .andExpect(
            jsonPath("$[?(@.id == '" + confirmed + "')].status").value(contains("IN_PROGRESS")));

    // Завершение одного приёма не трогает другой.
    setStatus(booked, "COMPLETED", "DOCTOR").andExpect(status().isOk());
    day("2026-11-26")
        .andExpect(jsonPath("$[?(@.id == '" + booked + "')].status").value(contains("COMPLETED")))
        .andExpect(
            jsonPath("$[?(@.id == '" + confirmed + "')].status").value(contains("IN_PROGRESS")));

    // Повторный старт идущего приёма — тот же encounter; завершённый не стартует.
    start(confirmed, DMITRY, PETROV)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.encounterId").value(JsonPath.<String>read(second, "$.encounterId")));
    start(booked, DMITRY, PETROV)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_STARTABLE"));
  }

  @Test
  void cancelledAndNoShowAppointmentsCannotBeStarted() throws Exception {
    String cancelled = bookedAppointment("2026-11-24T12:00:00Z", "2026-11-24T12:30:00Z");
    mvc.perform(post("/api/v1/appointments/" + cancelled + "/cancel").with(staff.admin()))
        .andExpect(status().isOk());
    String missed = bookedAppointment("2026-11-24T13:00:00Z", "2026-11-24T13:30:00Z");
    setStatus(missed, "NO_SHOW", "ADMINISTRATOR").andExpect(status().isOk());

    start(cancelled, DMITRY, PETROV)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_STARTABLE"));
    start(missed, DMITRY, PETROV)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_STARTABLE"));
  }

  @Test
  void savesDraftSignsFromNoteAndAmendsSignedDocument() throws Exception {
    String appointment = arrivedAppointment("2026-11-17T10:00:00Z", "2026-11-17T10:30:00Z");
    String started =
        start(appointment, DMITRY, PETROV).andReturn().getResponse().getContentAsString();
    String encounterId = JsonPath.read(started, "$.encounterId");
    String documents = "/api/v1/clinical/encounters/" + encounterId + "/documents";

    doctor(post(documents).contentType(MediaType.APPLICATION_JSON).content(TEMPLATE))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NOTE_REQUIRED"));

    doctor(
            put("/api/v1/clinical/encounters/" + encounterId + "/note")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"complaints":"Synthetic complaints","mentalStatus":"Synthetic status",
                     "diagnosis":"Synthetic diagnosis","recommendations":"Synthetic advice"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.diagnosis").value("Synthetic diagnosis"));

    String draft =
        doctor(post(documents).contentType(MediaType.APPLICATION_JSON).content(TEMPLATE))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.state").value("DRAFT"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String documentId = JsonPath.read(draft, "$.id");

    doctor(
            post("/api/v1/clinical/documents/" + documentId + "/amendments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEMPLATE))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ORIGINAL_NOT_SIGNED"));

    doctor(post("/api/v1/clinical/documents/" + documentId + "/sign"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SIGNED_LOCKED"))
        .andExpect(jsonPath("$.contentHash").isNotEmpty());

    doctor(
            post("/api/v1/clinical/documents/" + documentId + "/amendments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEMPLATE))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.state").value("DRAFT"))
        .andExpect(jsonPath("$.amendsDocumentId").value(documentId));

    doctor(get("/api/v1/clinical/patients/" + DMITRY + "/encounters"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.encounterId == '" + encounterId + "')].documents[*].state")
                .value(contains("SIGNED_LOCKED", "DRAFT")))
        .andExpect(
            jsonPath("$[?(@.encounterId == '" + encounterId + "')].documents[*].amendsDocumentId")
                .value(hasItem(documentId)))
        .andExpect(jsonPath("$[*].documents[*].snapshotEncrypted").isEmpty())
        .andExpect(jsonPath("$[*].documents[*].contentHash").isEmpty());
  }

  @Test
  void historyRequiresActiveCareRelationship() throws Exception {
    String created =
        mvc.perform(
                post("/api/v1/patients")
                    .with(staff.role("ADMINISTRATOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fullName":"No Link","birthDate":"1970-01-01","phone":"+70000000002",
                         "consentVersion":"v1.2","consentBasis":"SIGNED_FORM"}
                        """))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String patientId = JsonPath.read(created, "$.id");

    doctor(get("/api/v1/clinical/patients/" + patientId + "/encounters"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("No active care relationship with the patient"));
    mvc.perform(
            get("/api/v1/clinical/patients/" + DMITRY + "/encounters")
                .with(staff.role("ADMINISTRATOR")))
        .andExpect(status().isForbidden());
    // Кассир не открывает клиническую историю, даже если знает id пациента.
    mvc.perform(
            get("/api/v1/clinical/patients/" + DMITRY + "/encounters").with(staff.role("CASHIER")))
        .andExpect(status().isForbidden());
  }
}
