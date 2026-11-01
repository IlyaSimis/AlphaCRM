package ru.quietclinic.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
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
import ru.quietclinic.SecondClinic;
import ru.quietclinic.StaffSessions;

/**
 * Администратор заводит сотрудника с временным паролем, выключает его и задаёт новый временный
 * пароль; остальные — нет. Сотрудники другой клиники не видны.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StaffControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  private ResultActions create(String json) throws Exception {
    return mvc.perform(
        post("/api/v1/staff")
            .with(staff.admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private int loginStatus(String username, String password) throws Exception {
    String token = staff.csrfToken();
    return mvc.perform(
            post("/api/v1/auth/login")
                .cookie(new Cookie("XSRF-TOKEN", token))
                .header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  void deactivatedStaffCannotLogInAndLosesOpenSession() throws Exception {
    String created =
        create(
                """
                {"fullName":"Temporary Cashier","username":"temp.cashier","password":"temp-pass-1",
                 "role":"CASHIER"}
                """)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.practitionerId").doesNotExist())
            .andExpect(jsonPath("$.mustChangePassword").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(created, "$.id");
    StaffSessions.Login session = staff.login("temp.cashier", "temp-pass-1");
    String hash =
        jdbc.queryForObject(
            "select password_hash from staff_user where id = ?", String.class, UUID.fromString(id));
    assertThat(hash).startsWith("$2").doesNotContain("temp-pass-1");

    mvc.perform(post("/api/v1/staff/" + id + "/deactivate").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    assertThat(loginStatus("temp.cashier", "temp-pass-1")).isEqualTo(401);
    mvc.perform(get("/api/v1/auth/me").with(session)).andExpect(status().isUnauthorized());

    mvc.perform(post("/api/v1/staff/" + id + "/activate").with(staff.admin()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/staff/" + id + "/password")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"reset-pass-9\"}"))
        .andExpect(status().isNoContent());
    assertThat(loginStatus("temp.cashier", "reset-pass-9")).isEqualTo(200);
    // Новый пароль от администратора — снова временный: до смены остальные страницы закрыты.
    StaffSessions.Login again = staff.login("temp.cashier", "reset-pass-9");
    mvc.perform(get("/api/v1/auth/me").with(again))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));
    mvc.perform(get("/api/v1/patients").with(again))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
  }

  @Test
  void doctorNeedsExistingFreePractitionerAndOthersMustNotHaveOne() throws Exception {
    staff.doctor(StaffSessions.PETROV);
    create(
            """
            {"fullName":"Doctor Without Card","username":"doctor.none","password":"doctor-pass-1",
             "role":"DOCTOR"}
            """)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRACTITIONER_REQUIRED"));
    create(
            """
            {"fullName":"Doctor Unknown","username":"doctor.unknown","password":"doctor-pass-1",
             "role":"DOCTOR","practitionerId":"00000000-0000-0000-0000-000000009999"}
            """)
        .andExpect(status().isNotFound());
    // Врач чужой клиники — как несуществующий.
    SecondClinic.ensure(jdbc);
    create(
            """
            {"fullName":"Doctor Other Clinic","username":"doctor.other","password":"doctor-pass-1",
             "role":"DOCTOR","practitionerId":"%s"}
            """
                .formatted(SecondClinic.PRACTITIONER))
        .andExpect(status().isNotFound());
    create(
            """
            {"fullName":"Second Petrov","username":"petrov.two","password":"doctor-pass-1",
             "role":"DOCTOR","practitionerId":"00000000-0000-0000-0000-000000000201"}
            """)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRACTITIONER_TAKEN"));
    create(
            """
            {"fullName":"Cashier Doctor","username":"cashier.doctor","password":"cashier-pass-1",
             "role":"CASHIER","practitionerId":"00000000-0000-0000-0000-000000000201"}
            """)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRACTITIONER_NOT_ALLOWED"));
    create(
            """
            {"fullName":"Duplicate","username":"admin","password":"dupe-pass-1","role":"MANAGER"}
            """)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
  }

  @Test
  void administratorCannotDeactivateSelf() throws Exception {
    String adminId = staff.userId(StaffSessions.ADMIN);
    mvc.perform(post("/api/v1/staff/" + adminId + "/deactivate").with(staff.admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));
  }

  @Test
  void otherRolesCannotManageStaffOrReadHashes() throws Exception {
    String adminId = staff.userId(StaffSessions.ADMIN);
    // Менеджер видит сотрудников (identity.staff.read), кассир — нет.
    mvc.perform(get("/api/v1/staff").with(staff.role("MANAGER"))).andExpect(status().isOk());
    mvc.perform(get("/api/v1/staff").with(staff.role("CASHIER"))).andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/staff")
                .with(staff.doctor(StaffSessions.PETROV))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"Intruder","username":"intruder","password":"intruder-1",
                     "role":"ADMINISTRATOR"}
                    """))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/staff/" + adminId + "/deactivate").with(staff.role("CASHIER")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/staff").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].passwordHash").isEmpty())
        .andExpect(jsonPath("$[?(@.username == 't.petrov')].role").value("DOCTOR"));
  }

  @Test
  void demoLoginsFromOldMigrationsAreGone() throws Exception {
    for (String username : new String[] {"smirnova", "kuznetsov", "orlova", "petrov", "lebedeva"}) {
      assertThat(loginStatus(username, "clinic-dev")).isEqualTo(401);
    }
  }

  @Test
  void staffListAndChangesStayInsideTheClinic() throws Exception {
    SecondClinic.ensure(jdbc);
    StaffSessions.Login other =
        staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    mvc.perform(get("/api/v1/staff").with(other))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].username").value(contains(SecondClinic.ADMIN_USERNAME)));
    mvc.perform(get("/api/v1/auth/me").with(other))
        .andExpect(jsonPath("$.clinicName").value(SecondClinic.NAME));
    String firstAdmin = staff.userId(StaffSessions.ADMIN);
    mvc.perform(post("/api/v1/staff/" + firstAdmin + "/deactivate").with(other))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/staff").with(staff.admin()))
        .andExpect(jsonPath("$[*].username").value(not(hasItem(SecondClinic.ADMIN_USERNAME))));
  }

  private long practitionersNamed(String fullName) {
    Long count =
        jdbc.queryForObject(
            "select count(*) from practitioner where full_name = ?", Long.class, fullName);
    return count == null ? 0 : count;
  }

  @Test
  void doctorAccountAndNewCardWithRoomAndShiftComeFromOneRequest() throws Exception {
    String created =
        create(
                """
                {"fullName":"Новый Врач","username":"new.doctor","password":"doctor-pass-1",
                 "role":"DOCTOR","newPractitioner":{"specialty":"Психотерапевт","room":" 204 ",
                 "shifts":[{"dayOfWeek":3,"startsAt":"09:00","endsAt":"15:00"}]}}
                """)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(created, "$.id");
    String practitionerId = JsonPath.read(created, "$.practitionerId");
    // Как у существующих врачей: id учётной записи совпадает с id карточки.
    assertThat(id).isEqualTo(practitionerId);
    String card = "$[?(@.id == '" + practitionerId + "')]";
    mvc.perform(get("/api/v1/practitioners").with(staff.admin()))
        .andExpect(jsonPath(card + ".fullName").value(contains("Новый Врач")))
        .andExpect(jsonPath(card + ".room").value(contains("204")))
        .andExpect(jsonPath(card + ".workingHours[0].dayOfWeek").value(contains(3)));
    String reason =
        jdbc.queryForObject(
            "select reason from audit_event where action = 'PRACTITIONER_CREATED'"
                + " and object_id = ?",
            String.class,
            UUID.fromString(practitionerId));
    assertThat(reason).isEqualTo("fields:fullName,specialty,room,shifts");

    // Пустой кабинет хранится как NULL; специальность и кабинет меняются отдельно от графика.
    mvc.perform(
            put("/api/v1/practitioners/" + practitionerId)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specialty\":\"Психиатр\",\"room\":\"  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.specialty").value("Психиатр"))
        .andExpect(jsonPath("$.room").doesNotExist())
        .andExpect(jsonPath("$.workingHours.length()").value(1));
    assertThat(
            jdbc.queryForObject(
                "select room from practitioner where id = ?",
                String.class,
                UUID.fromString(practitionerId)))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "select reason from audit_event where action = 'PRACTITIONER_UPDATED'"
                    + " and object_id = ?",
                String.class,
                UUID.fromString(practitionerId)))
        .isEqualTo("fields:specialty,room");

    // Чужая клиника не видит и не меняет карточку.
    SecondClinic.ensure(jdbc);
    StaffSessions.Login other =
        staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    mvc.perform(
            put("/api/v1/practitioners/" + practitionerId)
                .with(other)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specialty\":\"Чужой\",\"room\":\"1\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PRACTITIONER_NOT_FOUND"));
  }

  @Test
  void managerCreatesDoctorAndScheduleCardInOneRequest() throws Exception {
    String created =
        mvc.perform(
                post("/api/v1/staff")
                    .with(staff.role("MANAGER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fullName":"Врач Менеджера","username":"manager.doctor","password":"doctor-pass-1",
                         "role":"DOCTOR","newPractitioner":{"specialty":"Психиатр","room":"305",
                         "shifts":[{"dayOfWeek":4,"startsAt":"10:00","endsAt":"18:00"}]}}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String practitionerId = JsonPath.read(created, "$.practitionerId");
    String card = "$[?(@.id == '" + practitionerId + "')]";
    mvc.perform(get("/api/v1/practitioners").with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath(card + ".fullName").value(contains("Врач Менеджера")))
        .andExpect(jsonPath(card + ".room").value(contains("305")))
        .andExpect(jsonPath(card + ".workingHours[0].dayOfWeek").value(contains(4)));
    assertThat(practitionersNamed("Врач Менеджера")).isEqualTo(1);
  }

  @Test
  void failedDoctorAccountLeavesNoPractitionerCard() throws Exception {
    create(
            """
            {"fullName":"Врач Занятый Логин","username":"admin","password":"doctor-pass-1",
             "role":"DOCTOR","newPractitioner":{"specialty":"Психиатр","room":"1","shifts":[]}}
            """)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    assertThat(practitionersNamed("Врач Занятый Логин")).isZero();

    create(
            """
            {"fullName":"Врач Неверная Смена","username":"bad.shift","password":"doctor-pass-1",
             "role":"DOCTOR","newPractitioner":{"specialty":"Психиатр","shifts":[
               {"dayOfWeek":2,"startsAt":"15:00","endsAt":"09:00"}]}}
            """)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_SHIFT"));
    create(
            """
            {"fullName":"Врач Неверная Смена","username":"bad.shift","password":"doctor-pass-1",
             "role":"DOCTOR","newPractitioner":{"specialty":"Психиатр","shifts":[
               {"dayOfWeek":2,"startsAt":"09:00","endsAt":"12:00"},
               {"dayOfWeek":2,"startsAt":"13:00","endsAt":"15:00"}]}}
            """)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_SHIFT"));
    assertThat(practitionersNamed("Врач Неверная Смена")).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from staff_user where username = 'bad.shift'", Long.class))
        .isZero();

    // Либо существующая карточка, либо новая — не обе сразу; и только для роли «Врач».
    create(
            """
            {"fullName":"Врач Обе Карточки","username":"both.cards","password":"doctor-pass-1",
             "role":"DOCTOR","practitionerId":"%s",
             "newPractitioner":{"specialty":"Психиатр","shifts":[]}}
            """
                .formatted(StaffSessions.LEBEDEVA))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRACTITIONER_AMBIGUOUS"));
    create(
            """
            {"fullName":"Кассир С Карточкой","username":"cashier.card","password":"cashier-pass-1",
             "role":"CASHIER","newPractitioner":{"specialty":"Психиатр","shifts":[]}}
            """)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRACTITIONER_NOT_ALLOWED"));
    assertThat(practitionersNamed("Врач Обе Карточки")).isZero();
    assertThat(practitionersNamed("Кассир С Карточкой")).isZero();
  }

  @Test
  void standaloneCardIsBoundToAccountLater() throws Exception {
    mvc.perform(
            post("/api/v1/practitioners")
                .with(staff.role("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"Карточка Менеджера","specialty":"Психиатр","shifts":[]}
                    """))
        .andExpect(status().isForbidden());
    String card =
        mvc.perform(
                post("/api/v1/practitioners")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fullName":"Карточка Без Входа","specialty":"Психиатр","room":"",
                         "shifts":[{"dayOfWeek":5,"startsAt":"10:00","endsAt":"18:00"}]}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.room").doesNotExist())
            .andExpect(jsonPath("$.workingHours.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String practitionerId = JsonPath.read(card, "$.id");
    // Чужая клиника не может привязать к этой карточке своего врача.
    SecondClinic.ensure(jdbc);
    StaffSessions.Login other =
        staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    mvc.perform(
            post("/api/v1/staff")
                .with(other)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"Чужой Врач","username":"foreign.bind","password":"doctor-pass-1",
                     "role":"DOCTOR","practitionerId":"%s"}
                    """
                        .formatted(practitionerId)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PRACTITIONER_NOT_FOUND"));
    create(
            """
            {"fullName":"Карточка Без Входа","username":"bound.doctor","password":"doctor-pass-1",
             "role":"DOCTOR","practitionerId":"%s"}
            """
                .formatted(practitionerId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(practitionerId))
        .andExpect(jsonPath("$.practitionerId").value(practitionerId));
    assertThat(practitionersNamed("Карточка Без Входа")).isEqualTo(1);
  }
}
