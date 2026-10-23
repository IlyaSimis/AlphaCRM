package ru.quietclinic.audit;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.quietclinic.SecondClinic;
import ru.quietclinic.StaffSessions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuditControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void recordsWhoChangedWhatWithoutValues() throws Exception {
    mvc.perform(
            put("/api/v1/patients/00000000-0000-0000-0000-000000000102")
                .with(staff.role("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"Дмитрий Волков","birthDate":"1985-11-02","phone":"+79030000001"}
                    """))
        .andExpect(status().isOk());

    var admin = staff.userId(StaffSessions.ADMIN);
    mvc.perform(get("/api/v1/audit").with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.action == 'PATIENT_UPDATED')].reason").value(hasItem("phone")))
        .andExpect(jsonPath("$[?(@.action == 'PATIENT_UPDATED')].actorId").value(hasItem(admin)));
  }

  @Test
  void showsOnlyActionsOfOwnClinicStaff() throws Exception {
    SecondClinic.ensure(jdbc);
    var other = staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    mvc.perform(get("/api/v1/patients").with(other)).andExpect(status().isOk());
    var otherAdmin =
        JsonPath.<String>read(
            mvc.perform(get("/api/v1/auth/me").with(other))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    mvc.perform(get("/api/v1/audit").with(staff.role("MANAGER")))
        .andExpect(jsonPath("$[*].actorId").value(not(hasItem(otherAdmin))));
  }

  @Test
  void filtersByResultAndIsClosedForDoctors() throws Exception {
    mvc.perform(get("/api/v1/audit").param("result", "DENIED").with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.result != 'DENIED')]").isEmpty());

    mvc.perform(get("/api/v1/audit").with(staff.role("DOCTOR"))).andExpect(status().isForbidden());
  }
}
