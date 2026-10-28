package ru.quietclinic.clinical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import org.springframework.test.web.servlet.ResultActions;
import ru.quietclinic.StaffSessions;

/**
 * Экстренный доступ владельца (K1): без доступа история закрыта; неверный пароль — отказ в журнале;
 * с доступом — только чтение этого пациента, основание чтения в журнале — break-glass; текст
 * причины виден в журнале экстренных доступов владельцу и старшему администратору, но не в аудите.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class BreakGlassTest {
  static final String ANNA = "00000000-0000-0000-0000-000000000101";
  static final String OTHER = "00000000-0000-0000-0000-000000000102";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void ownerReadsOnePatientAfterReasonAndPassword() throws Exception {
    var reason = "Проверка жалобы " + UUID.randomUUID();
    mvc.perform(get("/api/v1/clinical/patients/" + OTHER + "/encounters").with(staff.admin()))
        .andExpect(status().isForbidden());

    grant(ANNA, reason, "wrong-password")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
    grant(ANNA, reason, StaffSessions.ADMIN_PASSWORD)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.reasonText").isEmpty());
    mvc.perform(get("/api/v1/patients/" + ANNA + "/break-glass").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));

    mvc.perform(get("/api/v1/clinical/patients/" + ANNA + "/encounters").with(staff.admin()))
        .andExpect(status().isOk());
    // Только этот пациент.
    mvc.perform(get("/api/v1/clinical/patients/" + OTHER + "/encounters").with(staff.admin()))
        .andExpect(status().isForbidden());
    var reads =
        jdbc.queryForObject(
            "select count(*) from audit_event where action = 'CLINICAL_HISTORY_READ'"
                + " and object_id = ? and reason = 'break-glass'",
            Long.class,
            UUID.fromString(ANNA));
    assertThat(reads).isPositive();
    // Текст причины в журнал аудита не попадает.
    var leaked =
        jdbc.queryForObject(
            "select count(*) from audit_event where reason like ?", Long.class, "%" + reason + "%");
    assertThat(leaked).isZero();

    // Других ролей у экстренного доступа нет; журнал видят владелец и старший администратор.
    grant(ANNA, reason, StaffSessions.ADMIN_PASSWORD).andReturn();
    grantAs("ADMINISTRATOR", reason).andExpect(status().isForbidden());
    var today = LocalDate.now().toString();
    mvc.perform(
            get("/api/v1/clinical/break-glass")
                .with(staff.exactly("SENIOR_ADMINISTRATOR"))
                .param("from", LocalDate.now().minusDays(1).toString())
                .param("to", LocalDate.now().plusDays(1).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.reasonText == '" + reason + "')]").isNotEmpty());
    mvc.perform(
            get("/api/v1/clinical/break-glass")
                .with(staff.exactly("MANAGER"))
                .param("from", today)
                .param("to", today))
        .andExpect(status().isForbidden());
  }

  private ResultActions grant(String patient, String reason, String password) throws Exception {
    return mvc.perform(
        post("/api/v1/patients/" + patient + "/break-glass")
            .with(staff.admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(reason, password)));
  }

  private ResultActions grantAs(String role, String reason) throws Exception {
    return mvc.perform(
        post("/api/v1/patients/" + ANNA + "/break-glass")
            .with(staff.exactly(role))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(reason, "temporary-1")));
  }

  private static String body(String reason, String password) {
    return """
    {"reasonCode":"PATIENT_COMPLAINT","reasonText":"%s","password":"%s"}
    """
        .formatted(reason, password);
  }
}
