package ru.quietclinic;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Вход в тестах так же, как в браузере: cookie XSRF-TOKEN, POST /api/v1/auth/login, дальше — сессия
 * и CSRF-заголовок на каждом запросе.
 *
 * <p>Зашитых сотрудников нет: тесты входят как {@code admin / admin} (профиль local), а остальных
 * сотрудников заводят через API сотрудников с временным паролем и при первом входе меняют его — как
 * это делает человек.
 */
public final class StaffSessions {
  public static final String ADMIN = "admin";
  public static final String ADMIN_PASSWORD = "admin";

  /** Пароль, который тестовые сотрудники ставят себе после временного. */
  public static final String PASSWORD = "test-password-1";

  public static final String TEMPORARY_PASSWORD = "temporary-1";
  public static final String PETROV = "00000000-0000-0000-0000-000000000201";
  public static final String LEBEDEVA = "00000000-0000-0000-0000-000000000202";

  /** Тестовые сотрудники по ролям: логин, ФИО, роль, карточка врача. */
  private static final Map<String, List<String>> PEOPLE =
      Map.of(
          "t.manager", List.of("Test Manager", "MANAGER", ""),
          "t.cashier", List.of("Test Cashier", "CASHIER", ""),
          "t.petrov", List.of("Александр Петров", "DOCTOR", PETROV),
          "t.lebedeva", List.of("Ольга Лебедева", "DOCTOR", LEBEDEVA),
          "t.senior", List.of("Test Senior Administrator", "SENIOR_ADMINISTRATOR", ""),
          "t.admin", List.of("Test Administrator", "ADMINISTRATOR", ""),
          "t.nurse", List.of("Test Nurse", "NURSE", ""),
          "t.support", List.of("Test Support", "SUPPORT", ""));

  /**
   * Роль → сотрудник. ADMINISTRATOR — начальная учётная запись admin (она владелец клиники и может
   * всё, что администратор), как в тестах до появления роли OWNER.
   */
  private static final Map<String, String> BY_ROLE =
      Map.of(
          "OWNER", ADMIN,
          "ADMINISTRATOR", ADMIN,
          "SENIOR_ADMINISTRATOR", "t.senior",
          "MANAGER", "t.manager",
          "CASHIER", "t.cashier",
          "DOCTOR", "t.petrov",
          "NURSE", "t.nurse",
          "SUPPORT", "t.support");

  /** Сотрудник ровно этой роли — для матрицы RBAC (ADMINISTRATOR — t.admin, а не владелец). */
  private static final Map<String, String> EXACT =
      Map.of(
          "OWNER", ADMIN,
          "SENIOR_ADMINISTRATOR", "t.senior",
          "ADMINISTRATOR", "t.admin",
          "MANAGER", "t.manager",
          "CASHIER", "t.cashier",
          "DOCTOR", "t.petrov",
          "NURSE", "t.nurse",
          "SUPPORT", "t.support");

  private final MockMvc mvc;
  private final Map<String, Login> sessions = new HashMap<>();

  public StaffSessions(MockMvc mvc) {
    this.mvc = mvc;
  }

  /** Сессия сотрудника; тестового сотрудника заводит администратор, если его ещё нет. */
  public RequestPostProcessor as(String username) {
    // Не computeIfAbsent: вход тестового сотрудника сам открывает сессию администратора,
    // а вложенное изменение HashMap внутри computeIfAbsent даёт ConcurrentModificationException.
    var existing = sessions.get(username);
    if (existing != null) return existing;
    var login = signIn(username);
    sessions.put(username, login);
    return login;
  }

  /** ADMINISTRATOR и OWNER → admin, MANAGER → t.manager, CASHIER → t.cashier, DOCTOR → t.petrov. */
  public RequestPostProcessor role(String role) {
    return as(BY_ROLE.get(role));
  }

  /** Сотрудник ровно с этой ролью (матрица RBAC). */
  public RequestPostProcessor exactly(String role) {
    var username = EXACT.get(role);
    if (username == null) throw new IllegalArgumentException("Unknown role " + role);
    return as(username);
  }

  public RequestPostProcessor doctor(String practitionerId) {
    return as(LEBEDEVA.equals(practitionerId) ? "t.lebedeva" : "t.petrov");
  }

  /** Для DOCTOR — врач по id, для остальных ролей — сотрудник этой роли. */
  public RequestPostProcessor as(String role, String actor) {
    return "DOCTOR".equals(role) ? doctor(actor) : role(role);
  }

  public RequestPostProcessor admin() {
    return as(ADMIN);
  }

  /** Id учётной записи сотрудника (для назначения задач и проверок аудита). */
  public String userId(String username) {
    try {
      var body =
          mvc.perform(get("/api/v1/auth/me").with(as(username)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      return JsonPath.read(body, "$.id");
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  /** Только CSRF без сессии: для проверок «без входа». */
  public RequestPostProcessor anonymous() {
    var token = csrfToken();
    return request -> {
      request.setCookies(new Cookie("XSRF-TOKEN", token));
      request.addHeader("X-XSRF-TOKEN", token);
      return request;
    };
  }

  public String csrfToken() {
    try {
      var cookie =
          mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
      if (cookie == null) throw new IllegalStateException("Server did not issue XSRF-TOKEN");
      return cookie.getValue();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private Login signIn(String username) {
    if (ADMIN.equals(username)) return login(ADMIN, ADMIN_PASSWORD);
    var person = PEOPLE.get(username);
    if (person == null) throw new IllegalArgumentException("Unknown test staff member");
    if (!create(username, person.get(0), person.get(1), person.get(2), TEMPORARY_PASSWORD)) {
      return login(username, PASSWORD);
    }
    var first = login(username, TEMPORARY_PASSWORD);
    changePassword(first, TEMPORARY_PASSWORD, PASSWORD);
    return first;
  }

  /**
   * Заводит сотрудника от имени администратора. false — такой логин или врач уже заняты (контекст
   * Spring и база H2 общие для тестовых классов).
   */
  public boolean create(
      String username, String fullName, String role, String practitionerId, String password) {
    try {
      var practitioner = practitionerId.isEmpty() ? "null" : "\"" + practitionerId + "\"";
      var response =
          mvc.perform(
                  post("/api/v1/staff")
                      .with(admin())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {"fullName":"%s","username":"%s","password":"%s","role":"%s",
                           "practitionerId":%s}
                          """
                              .formatted(fullName, username, password, role, practitioner)))
              .andReturn()
              .getResponse();
      if (response.getStatus() == 201) return true;
      if (response.getStatus() == 409) return false;
      throw new IllegalStateException("Staff member not created: " + response.getStatus());
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  public void changePassword(Login login, String current, String next) {
    try {
      mvc.perform(
              post("/api/v1/auth/password")
                  .with(login)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}"))
          .andExpect(status().isNoContent());
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  public Login login(String username, String password) {
    try {
      var token = csrfToken();
      var result =
          mvc.perform(
                  post("/api/v1/auth/login")
                      .cookie(new Cookie("XSRF-TOKEN", token))
                      .header("X-XSRF-TOKEN", token)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
              .andExpect(status().isOk())
              .andReturn();
      var session = (MockHttpSession) result.getRequest().getSession(false);
      var rotated = result.getResponse().getCookie("XSRF-TOKEN");
      var csrf = rotated != null && !rotated.getValue().isEmpty() ? rotated.getValue() : token;
      return new Login(session, csrf);
    } catch (Exception ex) {
      throw new IllegalStateException("Could not sign in as " + username, ex);
    }
  }

  /** Сессия после входа и CSRF-токен: добавляются к каждому запросу. */
  public record Login(MockHttpSession session, String csrf) implements RequestPostProcessor {
    @Override
    public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
      request.setSession(session);
      request.setCookies(new Cookie("XSRF-TOKEN", csrf));
      request.addHeader("X-XSRF-TOKEN", csrf);
      return request;
    }
  }
}
