package ru.quietclinic.patients;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class PatientUpdateTest {
  static final String MARIA = "/api/v1/patients/00000000-0000-0000-0000-000000000103";

  @Autowired MockMvc mvc;

  @Test
  void administratorUpdatesContactDataAndChangeIsAudited() throws Exception {
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

    mvc.perform(get("/api/v1/audit").header("X-Demo-Role", "MANAGER"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.action == 'PATIENT_UPDATED')].reason")
                .value(hasItem("fullName,phone")));

    mvc.perform(get("/api/v1/audit").header("X-Demo-Role", "DOCTOR"))
        .andExpect(status().isForbidden());
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
}
