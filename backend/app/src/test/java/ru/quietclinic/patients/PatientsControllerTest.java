package ru.quietclinic.patients;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PatientsControllerTest {
  static final String MARIA = "/api/v1/patients/00000000-0000-0000-0000-000000000103";

  @Autowired MockMvc mvc;

  @Test
  void onlyAdministratorUpdatesContactData() throws Exception {
    var body =
        """
        {"fullName":"Мария Соколова-Ершова","birthDate":"1996-02-28","phone":"+79250000000"}
        """;
    mvc.perform(
            put(MARIA)
                .header("X-Demo-Role", "DOCTOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    mvc.perform(
            put(MARIA)
                .header("X-Demo-Role", "ADMINISTRATOR")
                .header("X-Actor-Id", "00000000-0000-0000-0000-000000000901")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Мария Соколова-Ершова"))
        .andExpect(jsonPath("$.contacts[0].value").value("+79250000000"));

    mvc.perform(get(MARIA).header("X-Demo-Role", "CASHIER"))
        .andExpect(jsonPath("$.fullName").value("Мария Соколова-Ершова"));
  }

  @Test
  void rejectsInvalidUpdate() throws Exception {
    mvc.perform(
            put(MARIA)
                .header("X-Demo-Role", "ADMINISTRATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\" \",\"birthDate\":\"2999-01-01\",\"phone\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void administratorGrantsAndRevokesConsentWithoutDeletingHistory() throws Exception {
    var created =
        mvc.perform(
                post("/api/v1/patients")
                    .header("X-Demo-Role", "ADMINISTRATOR")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fullName":"Согласие Тест","birthDate":"1988-08-08","phone":"+70000000003",
                         "consentVersion":"v1.2","consentBasis":"SIGNED_FORM"}
                        """))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(created, "$.id");
    String firstConsent = JsonPath.read(created, "$.consents[0].id");
    var consents = "/api/v1/patients/" + id + "/consents";

    mvc.perform(
            post(consents)
                .header("X-Demo-Role", "CASHIER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"v1.3\",\"basis\":\"SIGNED_FORM\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(consents)
                .header("X-Demo-Role", "ADMINISTRATOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"v1.3\",\"basis\":\"SIGNED_FORM\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.consents.length()").value(2));

    var revoke = consents + "/" + firstConsent + "/revoke";
    mvc.perform(post(revoke).header("X-Demo-Role", "ADMINISTRATOR"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.consents[?(@.id == '" + firstConsent + "')].revokedAt").isNotEmpty());
    mvc.perform(post(revoke).header("X-Demo-Role", "ADMINISTRATOR"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_REVOKED"));
    mvc.perform(
            post(consents + "/00000000-0000-0000-0000-000000009999/revoke")
                .header("X-Demo-Role", "ADMINISTRATOR"))
        .andExpect(status().isNotFound());
  }
}
