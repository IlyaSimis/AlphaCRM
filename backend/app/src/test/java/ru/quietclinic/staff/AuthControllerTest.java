package ru.quietclinic.staff;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.quietclinic.StaffSessions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthControllerTest {
  @Autowired MockMvc mvc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  private ResultActions login(String username, String password) throws Exception {
    String token = staff.csrfToken();
    return mvc.perform(
        post("/api/v1/auth/login")
            .cookie(new Cookie("XSRF-TOKEN", token))
            .header("X-XSRF-TOKEN", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
  }

  @Test
  void repeatedFailuresCloseSignInForThatLogin() throws Exception {
    for (int i = 0; i < 5; i++) {
      login("throttle.target", "wrong-" + i).andExpect(status().isUnauthorized());
    }
    login("throttle.target", "wrong-again")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
    // Другой логин с того же адреса пока не закрыт.
    login("throttle.other", "wrong").andExpect(status().isUnauthorized());
  }

  @Test
  void meCarriesTheClinicTimeZone() throws Exception {
    mvc.perform(get("/api/v1/auth/me").with(staff.admin()))
        .andExpect(jsonPath("$.timeZone").value("UTC"));
  }

  @Test
  void apiWithoutSessionAnswers401AndIgnoresDemoHeaders() throws Exception {
    mvc.perform(get("/api/v1/patients"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    mvc.perform(
            get("/api/v1/patients")
                .header("X-Demo-Role", "ADMINISTRATOR")
                .header("X-Actor-Id", "00000000-0000-0000-0000-000000000901"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void wrongPasswordAndUnknownLoginGiveTheSameAnswer() throws Exception {
    String wrongPassword =
        login(StaffSessions.ADMIN, "not-the-password")
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String unknownUser =
        login("nobody", StaffSessions.ADMIN_PASSWORD)
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Assertions.assertThat((String) JsonPath.read(unknownUser, "$.code"))
        .isEqualTo(JsonPath.read(wrongPassword, "$.code"))
        .isEqualTo("INVALID_CREDENTIALS");
    String first = JsonPath.read(wrongPassword, "$.message");
    String second = JsonPath.read(unknownUser, "$.message");
    Assertions.assertThat(first).isEqualTo(second).isEqualTo(AuthService.INVALID_CREDENTIALS);
  }

  @Test
  void meReturnsSessionStaffWithoutPasswordHash() throws Exception {
    mvc.perform(get("/api/v1/auth/me").with(staff.doctor(StaffSessions.LEBEDEVA)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("t.lebedeva"))
        .andExpect(jsonPath("$.mustChangePassword").value(false))
        .andExpect(jsonPath("$.clinicName").value("AlphaCRM"))
        .andExpect(jsonPath("$.role").value("DOCTOR"))
        .andExpect(jsonPath("$.practitionerId").value(StaffSessions.LEBEDEVA))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void changingRequestWithoutCsrfHeaderIsRejected() throws Exception {
    StaffSessions.Login login = staff.login(StaffSessions.ADMIN, StaffSessions.ADMIN_PASSWORD);
    mvc.perform(
            post("/api/v1/patients")
                .session(login.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"No Token","birthDate":"1990-01-01","phone":"+70000000009",
                     "consentVersion":"v1.2","consentBasis":"SIGNED_FORM"}
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
  }

  @Test
  void logoutEndsTheSession() throws Exception {
    StaffSessions.Login login = staff.login(StaffSessions.ADMIN, StaffSessions.ADMIN_PASSWORD);
    mvc.perform(get("/api/v1/auth/me").with(login)).andExpect(status().isOk());
    mvc.perform(post("/api/v1/auth/logout").with(login)).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/auth/me").session(login.session()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void newLoginReplacesOnlyThePreviousSession() throws Exception {
    StaffSessions.Login previous = staff.login(StaffSessions.ADMIN, StaffSessions.ADMIN_PASSWORD);
    StaffSessions.Login current = staff.login(StaffSessions.ADMIN, StaffSessions.ADMIN_PASSWORD);

    mvc.perform(get("/api/v1/auth/me").with(previous))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("SESSION_REPLACED"));
    mvc.perform(get("/api/v1/auth/me").with(current)).andExpect(status().isOk());

    // Даже если выход старой сессии дошёл до обработчика, новую он отозвать не может.
    mvc.perform(post("/api/v1/auth/logout").with(previous));
    mvc.perform(get("/api/v1/auth/me").with(current)).andExpect(status().isOk());
  }

  @Test
  void staffChangesOwnPasswordWithCurrentOne() throws Exception {
    mvc.perform(
            post("/api/v1/staff")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"fullName":"Password Change","username":"password.change",
                     "password":"start-pass-1","role":"CASHIER"}
                    """))
        .andExpect(status().isCreated());
    StaffSessions.Login login = staff.login("password.change", "start-pass-1");
    String change = "/api/v1/auth/password";

    // Временный пароль: до смены видны только «кто я», смена пароля и выход.
    mvc.perform(get("/api/v1/auth/me").with(login))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));
    mvc.perform(get("/api/v1/patients").with(login))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    mvc.perform(
            post(change)
                .with(login)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"start-pass-1\",\"newPassword\":\"start-pass-1\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PASSWORD_NOT_CHANGED"));

    mvc.perform(
            post(change)
                .with(login)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong-one\",\"newPassword\":\"next-pass-2\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
    mvc.perform(
            post(change)
                .with(login)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"start-pass-1\",\"newPassword\":\"short\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(change)
                .with(login)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"start-pass-1\",\"newPassword\":\"next-pass-2\"}"))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/patients").with(login)).andExpect(status().isOk());
    login("password.change", "start-pass-1").andExpect(status().isUnauthorized());
    login("password.change", "next-pass-2")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(false))
        .andExpect(content().string(not(containsString("$2a$"))));
  }

  @Test
  void localAdministratorLogsInWithoutPasswordChange() throws Exception {
    login(StaffSessions.ADMIN, StaffSessions.ADMIN_PASSWORD)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("admin"))
        .andExpect(jsonPath("$.role").value("OWNER"))
        .andExpect(jsonPath("$.permissionsVersion").isNotEmpty())
        .andExpect(jsonPath("$.permissions").isArray())
        .andExpect(jsonPath("$.mustChangePassword").value(false));
    mvc.perform(get("/api/v1/patients").with(staff.admin())).andExpect(status().isOk());
  }
}
