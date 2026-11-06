package ru.quietclinic.staffchat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.quietclinic.SecondClinic;
import ru.quietclinic.StaffSessions;

/** Только сотрудники: членство, клиника и непрочитанные проверяются на каждом REST-запросе. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StaffChatControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void directConversationIsReusableAndUnreadMessagesAreMarkedRead() throws Exception {
    String manager = staff.userId("t.manager");
    String created =
        mvc.perform(
                post("/api/v1/staff-chat/conversations/direct")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"staffId\":\"" + manager + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("DIRECT"))
            .andExpect(jsonPath("$.title").value("Test Manager"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String conversation = JsonPath.read(created, "$.id");
    String repeated =
        mvc.perform(
                post("/api/v1/staff-chat/conversations/direct")
                    .with(staff.role("MANAGER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"staffId\":\"" + staff.userId(StaffSessions.ADMIN) + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(JsonPath.<String>read(repeated, "$.id")).isEqualTo(conversation);

    String message =
        mvc.perform(
                post("/api/v1/staff-chat/conversations/{id}/messages", conversation)
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"Смена закрыта\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.body").value("Смена закрыта"))
            .andExpect(jsonPath("$.own").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long messageId = JsonPath.<Number>read(message, "$.id").longValue();

    mvc.perform(get("/api/v1/staff-chat/conversations").with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + conversation + "')].unreadCount").value(hasItem(1)));
    mvc.perform(
            get("/api/v1/staff-chat/conversations/{id}/messages", conversation)
                .with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[-1].body").value("Смена закрыта"))
        .andExpect(jsonPath("$[-1].own").value(false));
    mvc.perform(
            post("/api/v1/staff-chat/conversations/{id}/read", conversation)
                .with(staff.role("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageId\":" + messageId + "}"))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/staff-chat/conversations").with(staff.role("MANAGER")))
        .andExpect(jsonPath("$[?(@.id == '" + conversation + "')].unreadCount").value(hasItem(0)));

    mvc.perform(
            get("/api/v1/staff-chat/conversations/{id}/messages", conversation)
                .with(staff.role("SUPPORT")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/staff-chat/conversations").with(staff.anonymous()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void groupIsVisibleOnlyToSelectedEmployees() throws Exception {
    String title = "Дежурная смена " + UUID.randomUUID();
    String body =
        "{\"title\":\""
            + title
            + "\",\"memberIds\":[\""
            + staff.userId("t.manager")
            + "\",\""
            + staff.userId("t.cashier")
            + "\"]}";
    String created =
        mvc.perform(
                post("/api/v1/staff-chat/conversations/group")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("GROUP"))
            .andExpect(jsonPath("$.title").value(title))
            .andExpect(jsonPath("$.members.length()").value(3))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(created, "$.id");

    mvc.perform(get("/api/v1/staff-chat/conversations").with(staff.role("CASHIER")))
        .andExpect(jsonPath("$[*].title").value(hasItem(title)));
    mvc.perform(get("/api/v1/staff-chat/conversations").with(staff.role("SUPPORT")))
        .andExpect(jsonPath("$[*].title").value(not(hasItem(title))));
    mvc.perform(
            post("/api/v1/staff-chat/conversations/{id}/messages", id)
                .with(staff.role("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Проверьте кассу\"}"))
        .andExpect(status().isCreated());
    mvc.perform(get("/api/v1/staff-chat/conversations").with(staff.role("MANAGER")))
        .andExpect(jsonPath("$[?(@.id == '" + id + "')].unreadCount").value(hasItem(1)));
  }

  @Test
  void colleaguesDoNotNeedStaffAdministrationPermissionAndClinicsAreIsolated() throws Exception {
    String manager = staff.userId("t.manager");
    mvc.perform(get("/api/v1/staff-chat/colleagues").with(staff.role("CASHIER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id").value(hasItem(manager)))
        .andExpect(jsonPath("$[*].username").isEmpty());

    mvc.perform(
            post("/api/v1/staff-chat/conversations/group")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"Слишком маленькая\",\"memberIds\":[\""
                        + staff.userId("t.manager")
                        + "\"]}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/staff-chat/conversations/direct")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":\"" + staff.userId(StaffSessions.ADMIN) + "\"}"))
        .andExpect(status().isConflict());

    SecondClinic.ensure(jdbc);
    StaffSessions.Login other =
        staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    mvc.perform(
            post("/api/v1/staff-chat/conversations/direct")
                .with(other)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":\"" + staff.userId(StaffSessions.ADMIN) + "\"}"))
        .andExpect(status().isNotFound());
  }
}
