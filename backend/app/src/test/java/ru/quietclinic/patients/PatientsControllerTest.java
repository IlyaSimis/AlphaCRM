package ru.quietclinic.patients;

import static org.hamcrest.Matchers.contains;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.quietclinic.SecondClinic;
import ru.quietclinic.StaffSessions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PatientsControllerTest {
  static final String MARIA = "/api/v1/patients/00000000-0000-0000-0000-000000000103";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void onlyAdministratorUpdatesContactData() throws Exception {
    var body =
        """
        {"fullName":"Maria Sokolova-Ershova","birthDate":"1996-02-28","phone":"+79250000000"}
        """;
    mvc.perform(
            put(MARIA)
                .with(staff.role("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    mvc.perform(
            put(MARIA)
                .with(staff.role("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Maria Sokolova-Ershova"))
        .andExpect(jsonPath("$.contacts[0].value").value("+79250000000"));

    mvc.perform(get(MARIA).with(staff.role("CASHIER")))
        .andExpect(jsonPath("$.fullName").value("Maria Sokolova-Ershova"));
  }

  @Test
  void rejectsInvalidUpdate() throws Exception {
    mvc.perform(
            put(MARIA)
                .with(staff.role("ADMINISTRATOR"))
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
                    .with(staff.role("ADMINISTRATOR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fullName":"Consent Test","birthDate":"1988-08-08","phone":"+70000000003",
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
                .with(staff.role("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"v1.3\",\"basis\":\"SIGNED_FORM\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(consents)
                .with(staff.role("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"v1.3\",\"basis\":\"SIGNED_FORM\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.consents.length()").value(2));

    var revoke = consents + "/" + firstConsent + "/revoke";
    mvc.perform(post(revoke).with(staff.role("ADMINISTRATOR")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.consents[?(@.id == '" + firstConsent + "')].revokedAt").isNotEmpty());
    mvc.perform(post(revoke).with(staff.role("ADMINISTRATOR")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_REVOKED"));
    mvc.perform(
            post(consents + "/00000000-0000-0000-0000-000000009999/revoke")
                .with(staff.role("ADMINISTRATOR")))
        .andExpect(status().isNotFound());
  }

  private String createPatient(String fullName, String phone) throws Exception {
    var created =
        mvc.perform(
                post("/api/v1/patients")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fullName":"%s","birthDate":"1985-05-05","phone":"%s",
                         "consentVersion":"v1.2","consentBasis":"SIGNED_FORM"}
                        """
                            .formatted(fullName, phone)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(created, "$.id");
  }

  @Test
  void registryFieldsAreSavedAndAuditedWithoutNoteText() throws Exception {
    var id = createPatient("Registry Test", "+79990000001");
    var registry = "/api/v1/patients/" + id + "/registry";

    mvc.perform(
            put(registry)
                .with(staff.role("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"referralSource\":\"WEBSITE\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            put(registry)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"referralSource\":\"TV\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_REFERRAL_SOURCE"));
    mvc.perform(
            put(registry)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"referralSource":"WEBSITE","registryNote":"Asks to call after 6 pm"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.referralSource").value("WEBSITE"))
        .andExpect(jsonPath("$.registryNote").value("Asks to call after 6 pm"));

    mvc.perform(get("/api/v1/audit").with(staff.role("MANAGER")))
        .andExpect(
            jsonPath("$[?(@.action == 'PATIENT_REGISTRY_UPDATED')].reason")
                .value(hasItem("referralSource,registryNote")))
        .andExpect(content().string(not(containsString("after 6 pm"))));
  }

  @Test
  void administratorConnectsTelegramChatIdThatIsNeverShown() throws Exception {
    var id = createPatient("Telegram Test", "+79990000071");
    var registry = "/api/v1/patients/" + id + "/registry";
    mvc.perform(
            put(registry)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"telegramChatId\":\"not-a-chat\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            put(registry)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"telegramChatId\":\" 987654321 \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.telegramConnected").value(true))
        .andExpect(content().string(not(containsString("987654321"))));
    var stored =
        jdbc.queryForObject(
            "select contact_value from patient_contact where patient_id = ? and type = 'TELEGRAM'",
            String.class,
            java.util.UUID.fromString(id));
    org.assertj.core.api.Assertions.assertThat(stored).isEqualTo("987654321");
    mvc.perform(get("/api/v1/patients/" + id).with(staff.doctor(StaffSessions.PETROV)))
        .andExpect(content().string(not(containsString("987654321"))));

    mvc.perform(
            put(registry)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"removeTelegram\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.telegramConnected").value(false));
    mvc.perform(get("/api/v1/audit").with(staff.role("MANAGER")))
        .andExpect(
            jsonPath("$[?(@.action == 'PATIENT_REGISTRY_UPDATED')].reason")
                .value(hasItem("telegram")))
        .andExpect(content().string(not(containsString("987654321"))));
  }

  @Test
  void warnsWhenPhoneBelongsToAnotherCard() throws Exception {
    var first = createPatient("Phone First", "+7 912 555-44-33");
    var second = createPatient("Phone Second", "89125554433");

    mvc.perform(get("/api/v1/patients/" + second).with(staff.admin()))
        .andExpect(jsonPath("$.phoneSharedWith[0].id").value(first))
        .andExpect(jsonPath("$.phoneSharedWith[0].fullName").value("Phone First"));
    mvc.perform(get("/api/v1/patients/" + first).with(staff.admin()))
        .andExpect(jsonPath("$.phoneSharedWith[0].id").value(second));
  }

  @Test
  void patientsOfAnotherClinicAreInvisible() throws Exception {
    SecondClinic.ensure(jdbc);
    var anna = "/api/v1/patients/00000000-0000-0000-0000-000000000101";
    mvc.perform(get("/api/v1/patients").with(staff.admin()))
        .andExpect(jsonPath("$[*].id").value(not(hasItem(SecondClinic.PATIENT))));
    mvc.perform(get("/api/v1/patients/" + SecondClinic.PATIENT).with(staff.admin()))
        .andExpect(status().isNotFound());
    // Совпадение телефона с карточкой другой клиники не показывается.
    mvc.perform(get(anna).with(staff.admin()))
        .andExpect(jsonPath("$.phoneSharedWith[*].id").value(not(hasItem(SecondClinic.PATIENT))));

    var other = staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    mvc.perform(get("/api/v1/patients").with(other))
        .andExpect(jsonPath("$[*].fullName").value(contains(SecondClinic.PATIENT_NAME)));
    mvc.perform(get(anna).with(other)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/practitioners").with(other))
        .andExpect(jsonPath("$[*].id").value(contains(SecondClinic.PRACTITIONER)));
    mvc.perform(get("/api/v1/appointments").param("date", "2026-10-20").with(other))
        .andExpect(jsonPath("$").isEmpty());
  }
}
