package ru.quietclinic.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
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
    var token = staff.csrfToken();
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
    var created =
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
    var session = staff.login("temp.cashier", "temp-pass-1");
    String hash =
        jdbc.queryForObject(
            "select password_hash from staff_user where id = ?",
            String.class,
            java.util.UUID.fromString(id));
    assertThat(hash).startsWith("$2").doesNotContain("temp-pass-1");

    mvc.perform(post("/api/v1/staff/" + id + "/deactivate").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    org.assertj.core.api.Assertions.assertThat(loginStatus("temp.cashier", "temp-pass-1"))
        .isEqualTo(401);
    mvc.perform(get("/api/v1/auth/me").with(session)).andExpect(status().isUnauthorized());

    mvc.perform(post("/api/v1/staff/" + id + "/activate").with(staff.admin()))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/staff/" + id + "/password")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"reset-pass-9\"}"))
        .andExpect(status().isNoContent());
    org.assertj.core.api.Assertions.assertThat(loginStatus("temp.cashier", "reset-pass-9"))
        .isEqualTo(200);
    // Новый пароль от администратора — снова временный: до смены остальные страницы закрыты.
    var again = staff.login("temp.cashier", "reset-pass-9");
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
    var adminId = staff.userId(StaffSessions.ADMIN);
    mvc.perform(post("/api/v1/staff/" + adminId + "/deactivate").with(staff.admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));
  }

  @Test
  void otherRolesCannotManageStaffOrReadHashes() throws Exception {
    var adminId = staff.userId(StaffSessions.ADMIN);
    mvc.perform(get("/api/v1/staff").with(staff.role("MANAGER"))).andExpect(status().isForbidden());
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
    for (var username : new String[] {"smirnova", "kuznetsov", "orlova", "petrov", "lebedeva"}) {
      assertThat(loginStatus(username, "clinic-dev")).isEqualTo(401);
    }
  }

  @Test
  void staffListAndChangesStayInsideTheClinic() throws Exception {
    SecondClinic.ensure(jdbc);
    var other = staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    mvc.perform(get("/api/v1/staff").with(other))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].username").value(contains(SecondClinic.ADMIN_USERNAME)));
    mvc.perform(get("/api/v1/auth/me").with(other))
        .andExpect(jsonPath("$.clinicName").value(SecondClinic.NAME));
    var firstAdmin = staff.userId(StaffSessions.ADMIN);
    mvc.perform(post("/api/v1/staff/" + firstAdmin + "/deactivate").with(other))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/staff").with(staff.admin()))
        .andExpect(jsonPath("$[*].username").value(not(hasItem(SecondClinic.ADMIN_USERNAME))));
  }
}
