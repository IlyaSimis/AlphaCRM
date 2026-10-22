package ru.quietclinic.clinical;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

  private String arrivedAppointment(String startsAt, String endsAt) throws Exception {
    var body =
        mvc.perform(
                post("/api/v1/appointments")
                    .header("X-Demo-Role", "ADMINISTRATOR")
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
                .header("X-Demo-Role", "ADMINISTRATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ARRIVED\"}"))
        .andExpect(status().isOk());
    return id;
  }

  private ResultActions start(String appointmentId, String patientId, String actor)
      throws Exception {
    return mvc.perform(
        post("/api/v1/clinical/encounters")
            .header("X-Demo-Role", "DOCTOR")
            .header("X-Actor-Id", actor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"appointmentId":"%s","patientId":"%s","practitionerId":"%s"}
                """
                    .formatted(appointmentId, patientId, actor)));
  }

  private ResultActions doctor(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.header("X-Demo-Role", "DOCTOR").header("X-Actor-Id", PETROV));
  }

  @Test
  void startsOnlyFromOwnArrivedAppointmentAndReusesEncounter() throws Exception {
    var appointment = arrivedAppointment("2026-11-17T09:00:00Z", "2026-11-17T09:30:00Z");

    start(appointment, DMITRY, LEBEDEVA)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_MISMATCH"));
    start(appointment, ANNA, PETROV)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_MISMATCH"));
    start("00000000-0000-0000-0000-000000009999", DMITRY, PETROV)
        .andExpect(status().isNotFound());

    var first =
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

  @Test
  void refusesToStartBeforePatientArrived() throws Exception {
    // 302 — подтверждённая запись Петрова к Дмитрию Волкову из seed.
    start("00000000-0000-0000-0000-000000000302", DMITRY, PETROV)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_ARRIVED"));
  }

  @Test
  void savesDraftSignsFromNoteAndAmendsSignedDocument() throws Exception {
    var appointment = arrivedAppointment("2026-11-17T10:00:00Z", "2026-11-17T10:30:00Z");
    var started =
        start(appointment, DMITRY, PETROV).andReturn().getResponse().getContentAsString();
    String encounterId = JsonPath.read(started, "$.encounterId");
    var documents = "/api/v1/clinical/encounters/" + encounterId + "/documents";

    doctor(post(documents).contentType(MediaType.APPLICATION_JSON).content(TEMPLATE))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NOTE_REQUIRED"));

    doctor(
            put("/api/v1/clinical/encounters/" + encounterId + "/note")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"complaints":"Синтетические жалобы","mentalStatus":"Синтетический статус",
                     "diagnosis":"Синтетический диагноз","recommendations":"Синтетические советы"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.diagnosis").value("Синтетический диагноз"));

    var draft =
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
    var created =
        mvc.perform(
                post("/api/v1/patients")
                    .header("X-Demo-Role", "ADMINISTRATOR")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fullName":"Без Связи","birthDate":"1970-01-01","phone":"+70000000002",
                         "consentVersion":"v1.2","consentBasis":"SIGNED_FORM"}
                        """))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String patientId = JsonPath.read(created, "$.id");

    doctor(get("/api/v1/clinical/patients/" + patientId + "/encounters"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Нет активной связи с лечением пациента"));
    mvc.perform(
            get("/api/v1/clinical/patients/" + DMITRY + "/encounters")
                .header("X-Demo-Role", "ADMINISTRATOR"))
        .andExpect(status().isForbidden());
  }
}
