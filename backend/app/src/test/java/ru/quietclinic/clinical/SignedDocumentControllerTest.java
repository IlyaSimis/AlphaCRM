package ru.quietclinic.clinical;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

/**
 * Архив подписанных документов: врач видит то, что подписал сам, менеджер — всё в своей клинике.
 * Текст — из снимка подписи, а не из текущей заметки. Администратор и кассир архив не видят.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SignedDocumentControllerTest {
  static final String PETROV = StaffSessions.PETROV;
  static final String LEBEDEVA = StaffSessions.LEBEDEVA;
  static final String MARIA = "00000000-0000-0000-0000-000000000103";
  static final String ARCHIVE = "/api/v1/clinical/signed-documents";
  static final String TEMPLATE = "{\"templateVersion\":\"v2.4\"}";

  @Autowired MockMvc mvc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  private ResultActions petrov(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(staff.doctor(PETROV)));
  }

  private String note(String diagnosis) {
    return """
        {"complaints":"Archived complaints","mentalStatus":"Archived status",
         "diagnosis":"%s","recommendations":"Archived advice"}
        """
        .formatted(diagnosis);
  }

  /** Петров принимает Марию, подписывает документ и возвращает id приёма и документа. */
  private String[] signedVisit(String startsAt, String endsAt, String diagnosis) throws Exception {
    String booked =
        mvc.perform(
                post("/api/v1/appointments")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"patientId":"%s","practitionerId":"%s","startsAt":"%s","endsAt":"%s",
                         "serviceName":"Повторный приём"}
                        """
                            .formatted(MARIA, PETROV, startsAt, endsAt)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String appointment = JsonPath.read(booked, "$.id");
    String started =
        petrov(
                post("/api/v1/clinical/encounters")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"appointmentId":"%s","patientId":"%s","practitionerId":"%s"}
                        """
                            .formatted(appointment, MARIA, PETROV)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String encounter = JsonPath.read(started, "$.encounterId");
    petrov(
            put("/api/v1/clinical/encounters/" + encounter + "/note")
                .contentType(MediaType.APPLICATION_JSON)
                .content(note(diagnosis)))
        .andExpect(status().isOk());
    String draft =
        petrov(
                post("/api/v1/clinical/encounters/" + encounter + "/documents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TEMPLATE))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String document = JsonPath.read(draft, "$.id");
    petrov(post("/api/v1/clinical/documents/" + document + "/sign")).andExpect(status().isOk());
    return new String[] {encounter, document};
  }

  @Test
  void archiveShowsSignedSnapshotNotCurrentNoteAndSignedAmendments() throws Exception {
    String[] visit =
        signedVisit("2026-12-22T16:00:00Z", "2026-12-22T16:30:00Z", "Diagnosis at signing");
    String encounter = visit[0];
    String original = visit[1];

    // Заметку правят после подписи — в архиве остаётся подписанный текст.
    petrov(
            put("/api/v1/clinical/encounters/" + encounter + "/note")
                .contentType(MediaType.APPLICATION_JSON)
                .content(note("Diagnosis after amendment")))
        .andExpect(status().isOk());
    String amendment =
        JsonPath.<String>read(
            petrov(
                    post("/api/v1/clinical/documents/" + original + "/amendments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TEMPLATE))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    // Черновик дополнения в архив не попадает.
    petrov(get(ARCHIVE)).andExpect(jsonPath("$[*].id").value(not(hasItem(amendment))));

    petrov(get(ARCHIVE + "/" + original))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.diagnosis").value("Diagnosis at signing"))
        .andExpect(jsonPath("$.complaints").value("Archived complaints"))
        .andExpect(jsonPath("$.mentalStatus").value("Archived status"))
        .andExpect(jsonPath("$.recommendations").value("Archived advice"))
        .andExpect(jsonPath("$.patientName").value(containsString("Мария")))
        .andExpect(jsonPath("$.practitionerName").value("Александр Петров"))
        .andExpect(jsonPath("$.templateVersion").value("v2.4"))
        .andExpect(jsonPath("$.signedAt").isNotEmpty())
        .andExpect(jsonPath("$.amendsDocumentId").doesNotExist());

    petrov(post("/api/v1/clinical/documents/" + amendment + "/sign")).andExpect(status().isOk());
    petrov(get(ARCHIVE + "/" + amendment))
        .andExpect(jsonPath("$.diagnosis").value("Diagnosis after amendment"))
        .andExpect(jsonPath("$.amendsDocumentId").value(original));
    petrov(get(ARCHIVE))
        .andExpect(jsonPath("$[*].id").value(hasItem(original)))
        .andExpect(jsonPath("$[*].id").value(hasItem(amendment)));
  }

  @Test
  void doctorSeesOnlyOwnSignaturesManagerSeesAllAndFilters() throws Exception {
    String document =
        signedVisit("2026-12-29T16:00:00Z", "2026-12-29T16:30:00Z", "Petrov diagnosis")[1];

    mvc.perform(get(ARCHIVE).with(staff.doctor(LEBEDEVA)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id").value(not(hasItem(document))));
    mvc.perform(get(ARCHIVE + "/" + document).with(staff.doctor(LEBEDEVA)))
        .andExpect(status().isNotFound());

    // Менеджеру связь с лечением не нужна: доступ даёт роль и факт подписи.
    mvc.perform(get(ARCHIVE).with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id").value(hasItem(document)));
    mvc.perform(get(ARCHIVE).param("practitionerId", LEBEDEVA).with(staff.role("MANAGER")))
        .andExpect(jsonPath("$[*].id").value(not(hasItem(document))));
    mvc.perform(get(ARCHIVE).param("practitionerId", PETROV).with(staff.role("MANAGER")))
        .andExpect(jsonPath("$[*].practitionerId").value(not(hasItem(LEBEDEVA))))
        .andExpect(jsonPath("$[*].id").value(hasItem(document)));
    mvc.perform(get(ARCHIVE + "/" + document).with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.diagnosis").value("Petrov diagnosis"));
  }

  @Test
  void administratorAndCashierGet403() throws Exception {
    mvc.perform(get(ARCHIVE).with(staff.admin())).andExpect(status().isForbidden());
    mvc.perform(get(ARCHIVE).with(staff.role("CASHIER"))).andExpect(status().isForbidden());
  }

  @Test
  void archiveIsReadOnlyAndAuditedWithoutText() throws Exception {
    String document =
        signedVisit("2027-01-05T16:00:00Z", "2027-01-05T16:30:00Z", "Secret diagnosis")[1];
    mvc.perform(get(ARCHIVE + "/" + document).with(staff.role("MANAGER")))
        .andExpect(status().isOk());
    // Менеджер ничего не подписывает и не правит.
    mvc.perform(
            post("/api/v1/clinical/documents/" + document + "/sign").with(staff.role("MANAGER")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/clinical/documents/" + document + "/amendments")
                .with(staff.role("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEMPLATE))
        .andExpect(status().isForbidden());

    mvc.perform(get("/api/v1/audit").with(staff.role("MANAGER")))
        .andExpect(
            jsonPath(
                    "$[?(@.action == 'SIGNED_DOCUMENT_READ' && @.objectId == '"
                        + document
                        + "')].reason")
                .value(hasItem("card")))
        .andExpect(content().string(not(containsString("Secret diagnosis"))));
  }
}
