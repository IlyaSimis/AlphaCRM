package ru.quietclinic.staff;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.quietclinic.StaffSessions;

/**
 * Несколько ролей на сотрудника (раздел C): права складываются и действуют в уже открытой сессии;
 * роли меняются только не выше своих, свои — нельзя, роль врача привязана к карточке.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StaffRolesTest {
  @Autowired MockMvc mvc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void rolesAddUpAndApplyToTheOpenSession() throws Exception {
    String username = "roles." + UUID.randomUUID().toString().substring(0, 8);
    staff.create(username, "Roles Test", "CASHIER", "", "temporary-1");
    StaffSessions.Login session = staff.login(username, "temporary-1");
    staff.changePassword(session, "temporary-1", "roles-password-1");
    String id =
        JsonPath.read(
                mvc.perform(get("/api/v1/auth/me").with(session))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(),
                "$.id")
            .toString();

    mvc.perform(get("/api/v1/inventory/balances").with(session)).andExpect(status().isForbidden());
    setRoles(id, staff.exactly("MANAGER"), "[\"CASHIER\",\"NURSE\"]")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("NURSE"))
        .andExpect(jsonPath("$.roles", contains("NURSE", "CASHIER")))
        .andExpect(jsonPath("$.permissions", hasItem("inventory.stock.read")))
        .andExpect(jsonPath("$.permissions", hasItem("finance.invoice.write")));
    // Та же сессия: права пересчитаны без нового входа.
    mvc.perform(get("/api/v1/inventory/balances").with(session)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/auth/me").with(session))
        .andExpect(jsonPath("$.roles", contains("NURSE", "CASHIER")));

    // Не выше своих: менеджер не назначает администратора, администратор — старшего.
    setRoles(id, staff.exactly("MANAGER"), "[\"CASHIER\",\"ADMINISTRATOR\"]")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ROLE_NOT_ASSIGNABLE"));
    setRoles(id, staff.exactly("ADMINISTRATOR"), "[\"SENIOR_ADMINISTRATOR\"]")
        .andExpect(status().isForbidden());
    setRoles(id, staff.admin(), "[\"SENIOR_ADMINISTRATOR\",\"CASHIER\"]")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("SENIOR_ADMINISTRATOR"));
    // Сотрудника со старшей ролью администратор больше не обслуживает.
    mvc.perform(
            MockMvcRequestBuilders.post("/api/v1/staff/" + id + "/password")
                .with(staff.exactly("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"temporary-9\"}"))
        .andExpect(status().isForbidden());

    setRoles(id, staff.admin(), "[\"DOCTOR\"]")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DOCTOR_ROLE_FIXED"));
    String self =
        JsonPath.read(
                mvc.perform(get("/api/v1/auth/me").with(staff.admin()))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(),
                "$.id")
            .toString();
    setRoles(self, staff.admin(), "[\"OWNER\",\"NURSE\"]")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWN_ROLES"));
    setRoles(id, staff.admin(), "[]").andExpect(status().isBadRequest());
  }

  private ResultActions setRoles(String id, RequestPostProcessor who, String roles)
      throws Exception {
    return mvc.perform(
        put("/api/v1/staff/" + id + "/roles")
            .with(who)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"roles\":" + roles + "}"));
  }
}
