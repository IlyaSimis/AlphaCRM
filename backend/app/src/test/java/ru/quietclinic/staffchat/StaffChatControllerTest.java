package ru.quietclinic.staffchat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.quietclinic.SecondClinic;
import ru.quietclinic.StaffSessions;

/** Только сотрудники: членство, клиника и непрочитанные проверяются на каждом REST-запросе. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StaffChatControllerTest {
  @TempDir static Path attachmentDirectory;

  @DynamicPropertySource
  static void attachmentStorage(DynamicPropertyRegistry registry) {
    registry.add("clinic.chat.storage-dir", () -> attachmentDirectory.toString());
  }

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
            .andExpect(jsonPath("$.readByAll").value(false))
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
        .andExpect(jsonPath("$[-1].own").value(false))
        .andExpect(jsonPath("$[-1].readByAll").value(false));
    mvc.perform(
            post("/api/v1/staff-chat/conversations/{id}/read", conversation)
                .with(staff.role("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageId\":" + messageId + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(messageId))
        .andExpect(jsonPath("$.readByAll").value(true));
    mvc.perform(
            get("/api/v1/staff-chat/conversations/{id}/messages", conversation).with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[-1].readByAll").value(true));
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
  void notificationPreferencesArePersonalAndPersistent() throws Exception {
    mvc.perform(get("/api/v1/staff-chat/preferences").with(staff.role("CASHIER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notificationsEnabled").value(true))
        .andExpect(jsonPath("$.soundEnabled").value(true))
        .andExpect(jsonPath("$.browserEnabled").value(false));

    mvc.perform(
            put("/api/v1/staff-chat/preferences")
                .with(staff.role("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"notificationsEnabled\":false,\"soundEnabled\":false,"
                        + "\"browserEnabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notificationsEnabled").value(false))
        .andExpect(jsonPath("$.soundEnabled").value(false))
        .andExpect(jsonPath("$.browserEnabled").value(true));

    mvc.perform(get("/api/v1/staff-chat/preferences").with(staff.role("CASHIER")))
        .andExpect(jsonPath("$.notificationsEnabled").value(false))
        .andExpect(jsonPath("$.browserEnabled").value(true));
    mvc.perform(get("/api/v1/staff-chat/preferences").with(staff.role("MANAGER")))
        .andExpect(jsonPath("$.notificationsEnabled").value(true))
        .andExpect(jsonPath("$.soundEnabled").value(true))
        .andExpect(jsonPath("$.browserEnabled").value(false));
  }

  @Test
  void mentionsAreStoredForMembersAndRejectedForOutsiders() throws Exception {
    String manager = staff.userId("t.manager");
    String created =
        mvc.perform(
                post("/api/v1/staff-chat/conversations/group")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"Упоминания "
                            + UUID.randomUUID()
                            + "\",\"memberIds\":[\""
                            + manager
                            + "\",\""
                            + staff.userId("t.cashier")
                            + "\"]}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String conversation = JsonPath.read(created, "$.id");

    mvc.perform(
            post("/api/v1/staff-chat/conversations/{id}/messages", conversation)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"text\":\"@Test Manager, проверьте запись\","
                        + "\"mentionIds\":[\""
                        + manager
                        + "\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.mentionIds[0]").value(manager))
        .andExpect(jsonPath("$.mentioned").value(false));
    mvc.perform(
            get("/api/v1/staff-chat/conversations/{id}/messages", conversation)
                .with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[-1].mentionIds[0]").value(manager))
        .andExpect(jsonPath("$[-1].mentioned").value(true));

    mvc.perform(
            post("/api/v1/staff-chat/conversations/{id}/messages", conversation)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"text\":\"Недопустимое упоминание\",\"mentionIds\":[\""
                        + staff.userId("t.support")
                        + "\"]}"))
        .andExpect(status().isBadRequest());
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

  @Test
  void authorEditsAndDeletesMessageWhileMembersToggleReactions() throws Exception {
    String conversation = conversationWithManager();
    long messageId = sendMessage(conversation, "Исходный текст");

    mvc.perform(
            patch("/api/v1/staff-chat/messages/{id}", messageId)
                .with(staff.role("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Чужая правка\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch("/api/v1/staff-chat/messages/{id}", messageId)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Исправленный текст\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value("Исправленный текст"))
        .andExpect(jsonPath("$.editedAt").isNotEmpty());

    mvc.perform(
            put("/api/v1/staff-chat/messages/{id}/reaction", messageId)
                .with(staff.role("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emoji\":\"👍\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reactions[0].emoji").value("👍"))
        .andExpect(jsonPath("$.reactions[0].count").value(1))
        .andExpect(jsonPath("$.reactions[0].own").value(true));
    mvc.perform(
            put("/api/v1/staff-chat/messages/{id}/reaction", messageId)
                .with(staff.role("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emoji\":\"👍\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reactions.length()").value(0));
    mvc.perform(
            put("/api/v1/staff-chat/messages/{id}/reaction", messageId)
                .with(staff.role("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emoji\":\"🔥\"}"))
        .andExpect(status().isBadRequest());

    mvc.perform(delete("/api/v1/staff-chat/messages/{id}", messageId).with(staff.role("MANAGER")))
        .andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/staff-chat/messages/{id}", messageId).with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value(""))
        .andExpect(jsonPath("$.deleted").value(true))
        .andExpect(jsonPath("$.attachments.length()").value(0))
        .andExpect(jsonPath("$.reactions.length()").value(0));
    mvc.perform(
            patch("/api/v1/staff-chat/messages/{id}", messageId)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Восстановление\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            put("/api/v1/staff-chat/messages/{id}/reaction", messageId)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emoji\":\"👍\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void attachmentsAreDownloadableOnlyForMembersAndUnavailableAfterDeletion() throws Exception {
    String conversation = conversationWithManager();
    byte[] bytes = "Содержимое файла".getBytes(StandardCharsets.UTF_8);
    MockMultipartFile file = new MockMultipartFile("files", "отчёт.txt", "text/plain", bytes);
    String created =
        mvc.perform(
                multipart("/api/v1/staff-chat/conversations/{id}/messages", conversation)
                    .file(file)
                    .param("text", "Файл во вложении")
                    .with(staff.admin()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.attachments[0].name").value("отчёт.txt"))
            .andExpect(jsonPath("$.attachments[0].sizeBytes").value(bytes.length))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long messageId = JsonPath.<Number>read(created, "$.id").longValue();
    String attachmentId = JsonPath.read(created, "$.attachments[0].id");

    mvc.perform(
            get("/api/v1/staff-chat/attachments/{id}", attachmentId).with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
        .andExpect(content().bytes(bytes))
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    org.hamcrest.Matchers.containsString("attachment")));
    mvc.perform(
            get("/api/v1/staff-chat/attachments/{id}", attachmentId).with(staff.role("SUPPORT")))
        .andExpect(status().isNotFound());

    mvc.perform(delete("/api/v1/staff-chat/messages/{id}", messageId).with(staff.admin()))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/staff-chat/attachments/{id}", attachmentId).with(staff.role("MANAGER")))
        .andExpect(status().isNotFound());
  }

  @Test
  void attachmentCountSizeAndMultipartTextAreLimited() throws Exception {
    String conversation = conversationWithManager();
    MockMultipartFile oversized =
        new MockMultipartFile(
            "files", "large.bin", "application/octet-stream", new byte[10 * 1024 * 1024 + 1]);
    mvc.perform(
            multipart("/api/v1/staff-chat/conversations/{id}/messages", conversation)
                .file(oversized)
                .with(staff.admin()))
        .andExpect(status().isBadRequest());

    org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder tooMany =
        multipart("/api/v1/staff-chat/conversations/{id}/messages", conversation);
    for (int index = 0; index < 6; index++) {
      tooMany.file(new MockMultipartFile("files", index + ".txt", "text/plain", new byte[] {1}));
    }
    mvc.perform(tooMany.with(staff.admin())).andExpect(status().isBadRequest());

    MockMultipartFile file =
        new MockMultipartFile("files", "note.txt", "text/plain", new byte[] {1});
    mvc.perform(
            multipart("/api/v1/staff-chat/conversations/{id}/messages", conversation)
                .file(file)
                .param("text", "x".repeat(4001))
                .with(staff.admin()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void messageHistoryReturnsTwoHundredNewestAndPagesOlderItems() throws Exception {
    String conversation = conversationWithManager();
    UUID adminId = UUID.fromString(staff.userId(StaffSessions.ADMIN));
    UUID clinic =
        jdbc.queryForObject("select clinic_id from staff_user where id = ?", UUID.class, adminId);
    for (int index = 1; index <= 205; index++) {
      jdbc.update(
          "insert into staff_chat_message(id, conversation_id, clinic_id, sender_id, body, created_at)"
              + " values (nextval('staff_chat_message_seq'), ?, ?, ?, ?, current_timestamp)",
          conversation,
          clinic,
          adminId,
          "Сообщение " + index);
    }

    mvc.perform(
            get("/api/v1/staff-chat/conversations/{id}/messages", conversation).with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(200))
        .andExpect(jsonPath("$[0].body").value("Сообщение 6"))
        .andExpect(jsonPath("$[199].body").value("Сообщение 205"));

    String first =
        mvc.perform(
                get("/api/v1/staff-chat/conversations/{id}/messages", conversation)
                    .with(staff.admin()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long beforeId = JsonPath.<Number>read(first, "$[0].id").longValue();
    mvc.perform(
            get("/api/v1/staff-chat/conversations/{id}/messages", conversation)
                .param("beforeId", String.valueOf(beforeId))
                .with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[0].body").value("Сообщение 1"))
        .andExpect(jsonPath("$[4].body").value("Сообщение 5"));
  }

  private String conversationWithManager() throws Exception {
    String created =
        mvc.perform(
                post("/api/v1/staff-chat/conversations/group")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\"Действия "
                            + UUID.randomUUID()
                            + "\",\"memberIds\":[\""
                            + staff.userId("t.manager")
                            + "\",\""
                            + staff.userId("t.cashier")
                            + "\"]}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(created, "$.id");
  }

  private long sendMessage(String conversation, String text) throws Exception {
    String created =
        mvc.perform(
                post("/api/v1/staff-chat/conversations/{id}/messages", conversation)
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"" + text + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.<Number>read(created, "$.id").longValue();
  }
}
