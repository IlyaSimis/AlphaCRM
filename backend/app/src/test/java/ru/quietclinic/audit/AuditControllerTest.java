package ru.quietclinic.audit;

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
class AuditControllerTest {
  static final String ADMIN = "00000000-0000-0000-0000-000000000901";

  @Autowired MockMvc mvc;

  @Test
  void recordsWhoChangedWhatWithoutValues() throws Exception {
    mvc.perform(
            put("/api/v1/patients/00000000-0000-0000-0000-000000000102")
                .header("X-Demo-Role", "ADMINISTRATOR")
                .header("X-Actor-Id", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"Дмитрий Волков","birthDate":"1985-11-02","phone":"+79030000001"}
                    """))
        .andExpect(status().isOk());

    mvc.perform(get("/api/v1/audit").header("X-Demo-Role", "MANAGER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.action == 'PATIENT_UPDATED')].reason").value(hasItem("phone")))
        .andExpect(jsonPath("$[?(@.action == 'PATIENT_UPDATED')].actorId").value(hasItem(ADMIN)));
  }

  @Test
  void filtersByResultAndIsClosedForDoctors() throws Exception {
    mvc.perform(get("/api/v1/audit").param("result", "DENIED").header("X-Demo-Role", "MANAGER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.result != 'DENIED')]").isEmpty());

    mvc.perform(get("/api/v1/audit").header("X-Demo-Role", "DOCTOR"))
        .andExpect(status().isForbidden());
  }
}
