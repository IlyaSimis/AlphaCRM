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

    // Кассиру — только ФИО (нет clinical.patient.contacts-read).
    mvc.perform(get(MARIA).with(staff.role("CASHIER")))
        .andExpect(jsonPath("$.fullName").value("Maria Sokolova-Ershova"))
        .andExpect(jsonPath("$.contacts").isEmpty())
        .andExpect(jsonPath("$.birthDate").value(org.hamcrest.Matchers.nullValue()));
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
  void newPatientNeedsPastBirthDateAndBoundedName() throws Exception {
    mvc.perform(
            post("/api/v1/patients")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"%s","birthDate":"2999-01-01","phone":"+70000000088",
                     "consentVersion":"v1.2","consentBasis":"SIGNED_FORM"}
                    """
                        .formatted("x".repeat(256))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.birthDate").exists())
        .andExpect(jsonPath("$.fields.fullName").exists());
  }

  @Test
  void patientListIsOneAuditEventNotOnePerPatient() throws Exception {
    mvc.perform(get("/api/v1/patients").with(staff.role("MANAGER"))).andExpect(status().isOk());
    var reasons =
        jdbc.queryForList(
            "select reason from audit_event where action = 'PATIENT_LIST_READ'"
                + " order by occurred_at desc",
            String.class);
    org.assertj.core.api.Assertions.assertThat(reasons).isNotEmpty();
    org.assertj.core.api.Assertions.assertThat(reasons.get(0)).startsWith("list:");
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

  @Test
  void searchFindsNameAndEitherPhoneAcrossFormatsWithinClinic() throws Exception {
    var id =
        createPatient(
            """
            {"fullName":"Search Target","birthDate":"1990-06-06","phone":"+7 912 555-40-01",
             "secondPhone":"8 (903) 555-40-02","consentVersion":"v1.2","consentBasis":"SIGNED_FORM"}
            """);
    for (var query : java.util.List.of("search target", "89125554001", "912 555", "89035554002")) {
      mvc.perform(get("/api/v1/patients").param("query", query).with(staff.admin()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].id").value(hasItem(id)));
    }

    SecondClinic.ensure(jdbc);
    mvc.perform(
            get("/api/v1/patients").param("query", SecondClinic.PATIENT_NAME).with(staff.admin()))
        .andExpect(jsonPath("$[*].id").value(not(hasItem(SecondClinic.PATIENT))));
    mvc.perform(get("/api/v1/patients").param("query", "89162451872").with(staff.admin()))
        .andExpect(jsonPath("$[*].id").value(not(hasItem(SecondClinic.PATIENT))));
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

  private String createPatient(String json) throws Exception {
    var body =
        mvc.perform(
                post("/api/v1/patients")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  @Test
  void emailSecondPhoneAndPermanentAdministratorAreStoredSeparately() throws Exception {
    var adminId = staff.userId(StaffSessions.ADMIN);
    var id =
        createPatient(
            """
            {"fullName":"Contacts Test","birthDate":"1990-03-03","phone":"+70000000301",
             "email":" contacts@example.org ","secondPhone":"8 (916) 245-18-72",
             "assignedStaffId":"%s","consentVersion":"v1.2","consentBasis":"SIGNED_FORM"}
            """
                .formatted(adminId));
    var card = "/api/v1/patients/" + id;
    // Второй телефон совпадает с основным у Анны Михайловой: предупреждение видно с обеих сторон.
    // Врачу без связи с лечением карточка не видна; менеджер видит административную карточку.
    mvc.perform(get(card).with(staff.doctor(StaffSessions.PETROV)))
        .andExpect(status().isNotFound());
    mvc.perform(get(card).with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("contacts@example.org"))
        .andExpect(jsonPath("$.secondPhone").value("8 (916) 245-18-72"))
        .andExpect(jsonPath("$.assignedStaff.id").value(adminId))
        .andExpect(jsonPath("$.assignedStaff.active").value(true))
        .andExpect(
            jsonPath("$.phoneSharedWith[*].id")
                .value(hasItem("00000000-0000-0000-0000-000000000101")));
    mvc.perform(get("/api/v1/patients/00000000-0000-0000-0000-000000000101").with(staff.admin()))
        .andExpect(jsonPath("$.phoneSharedWith[*].id").value(hasItem(id)));
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForList(
                "select type from patient_contact where patient_id = ?",
                String.class,
                java.util.UUID.fromString(id)))
        .containsExactlyInAnyOrder("PHONE", "EMAIL", "SECOND_PHONE");

    // Пустые необязательные поля убирают контакт, а не хранят пустую строку; в аудите — имена
    // полей.
    mvc.perform(
            put(card)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"Contacts Test","birthDate":"1990-03-03","phone":"+70000000301",
                     "email":"","secondPhone":"  ","assignedStaffId":null}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").doesNotExist())
        .andExpect(jsonPath("$.secondPhone").doesNotExist())
        .andExpect(jsonPath("$.assignedStaff").doesNotExist())
        .andExpect(jsonPath("$.phoneSharedWith").isEmpty());
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForList(
                "select contact_value from patient_contact where patient_id = ?",
                String.class,
                java.util.UUID.fromString(id)))
        .containsExactly("+70000000301");
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "select reason from audit_event where action = 'PATIENT_UPDATED'"
                    + " and object_id = ? order by occurred_at desc limit 1",
                String.class,
                java.util.UUID.fromString(id)))
        .isEqualTo("email,secondPhone,assignedStaff")
        .doesNotContain("@")
        .doesNotContain("916");
  }

  @Test
  void permanentAdministratorMustBeAnActiveAdministratorOfTheClinic() throws Exception {
    var body =
        """
        {"fullName":"Assignee Test","birthDate":"1990-04-04","phone":"+70000000302",
         "assignedStaffId":"%s"}
        """;
    var id =
        createPatient(
            """
            {"fullName":"Assignee Test","birthDate":"1990-04-04","phone":"+70000000302",
             "consentVersion":"v1.2","consentBasis":"SIGNED_FORM"}
            """);
    var card = "/api/v1/patients/" + id;
    var cashierId = staff.userId("t.cashier");
    mvc.perform(
            put(card)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(cashierId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_ASSIGNED_STAFF"));
    SecondClinic.ensure(jdbc);
    var foreignAdmin =
        jdbc.queryForObject(
            "select id from staff_user where username = ?",
            java.util.UUID.class,
            SecondClinic.ADMIN_USERNAME);
    mvc.perform(
            put(card)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(foreignAdmin)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("STAFF_NOT_FOUND"));
    mvc.perform(
            put(card)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"Assignee Test","birthDate":"1990-04-04","phone":"+70000000302",
                     "email":"not-an-email"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.email").exists());

    // Отключённый администратор остаётся привязанным, но заново его выбрать нельзя.
    staff.create("spare.admin", "Spare Administrator", "ADMINISTRATOR", "", "spare-admin-1");
    var spare =
        jdbc.queryForObject(
                "select id from staff_user where username = 'spare.admin'", java.util.UUID.class)
            .toString();
    mvc.perform(
            put(card)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(spare)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignedStaff.fullName").value("Spare Administrator"));
    mvc.perform(post("/api/v1/staff/" + spare + "/deactivate").with(staff.admin()))
        .andExpect(status().isOk());
    mvc.perform(get(card).with(staff.admin()))
        .andExpect(jsonPath("$.assignedStaff.id").value(spare))
        .andExpect(jsonPath("$.assignedStaff.active").value(false));
    mvc.perform(
            put(card)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(spare)))
        .andExpect(status().isOk());
    var other =
        createPatient(
            """
            {"fullName":"Assignee Other","birthDate":"1990-05-05","phone":"+70000000303",
             "assignedStaffId":"%s","consentVersion":"v1.2","consentBasis":"SIGNED_FORM"}
            """
                .formatted(staff.userId(StaffSessions.ADMIN)));
    mvc.perform(
            put("/api/v1/patients/" + other)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"Assignee Other","birthDate":"1990-05-05","phone":"+70000000303",
                     "assignedStaffId":"%s"}
                    """
                        .formatted(spare)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_ASSIGNED_STAFF"));
  }
}
