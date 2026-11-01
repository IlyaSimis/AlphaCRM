package ru.quietclinic.workspace;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import ru.quietclinic.StaffSessions;

/** Личное пространство: свободная структура, владелец записи и именованные представления. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class WorkspaceControllerTest {
  @Autowired MockMvc mvc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void freeFormItemIsVisibleOnlyToItsOwner() throws Exception {
    String title = "Разобраться " + UUID.randomUUID();
    String created =
        mvc.perform(
                post("/api/v1/workspace/items")
                    .with(staff.role("SUPPORT"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"title":"%s","type":"Свой тип","body":"Любой текст","status":"Позже",
                         "color":"purple","tags":["личное","платформа"],
                         "fields":[{"name":"Контекст","value":"Что угодно"}],
                         "checklist":[{"text":"Первый шаг","done":false}],"completed":false}
                        """
                            .formatted(title)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("Свой тип"))
            .andExpect(jsonPath("$.status").value("Позже"))
            .andExpect(jsonPath("$.fields[0].value").value("Что угодно"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.<String>read(created, "$.id");

    mvc.perform(get("/api/v1/workspace/items").with(staff.role("SUPPORT")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].title").value(hasItem(title)));
    mvc.perform(get("/api/v1/workspace/items").with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].title").value(not(hasItem(title))));
    mvc.perform(
            put("/api/v1/workspace/items/{id}", id)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Чужая запись\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void assignedTaskIsSharedByCreatorAndAssigneeOnly() throws Exception {
    String title = "Общая задача " + UUID.randomUUID();
    String assigneeId = staff.userId("t.petrov");
    String creatorId = staff.userId("t.manager");
    String created =
        mvc.perform(
                post("/api/v1/workspace/items")
                    .with(staff.role("MANAGER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"title\":\""
                            + title
                            + "\",\"type\":\"Задача\",\"assigneeId\":\""
                            + assigneeId
                            + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.creatorId").value(creatorId))
            .andExpect(jsonPath("$.assigneeId").value(assigneeId))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.<String>read(created, "$.id");

    mvc.perform(get("/api/v1/workspace/items").with(staff.role("MANAGER")))
        .andExpect(jsonPath("$[*].title").value(hasItem(title)));
    mvc.perform(get("/api/v1/workspace/items").with(staff.role("DOCTOR")))
        .andExpect(jsonPath("$[*].title").value(hasItem(title)));
    mvc.perform(get("/api/v1/workspace/items").with(staff.role("SUPPORT")))
        .andExpect(jsonPath("$[*].title").value(not(hasItem(title))));
    mvc.perform(get("/api/v1/workspace/assignees").with(staff.role("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id").value(hasItem(assigneeId)));

    mvc.perform(
            put("/api/v1/workspace/items/{id}", id)
                .with(staff.role("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\""
                        + title
                        + "\",\"type\":\"Задача\",\"completed\":true,"
                        + "\"assigneeId\":\""
                        + assigneeId
                        + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(true));
    mvc.perform(
            put("/api/v1/workspace/items/{id}", id)
                .with(staff.role("SUPPORT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Чужая задача\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void savedViewsAndArchiveBelongToOwner() throws Exception {
    String name = "Мой фильтр " + UUID.randomUUID();
    String created =
        mvc.perform(
                post("/api/v1/workspace/views")
                    .with(staff.role("CASHIER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","filter":{"query":"срок","type":"Задача","status":"Жду",
                         "tag":"касса","due":"week","sort":"updated","display":"board"}}
                        """
                            .formatted(name)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.filter.display").value("board"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String viewId = JsonPath.<String>read(created, "$.id");
    mvc.perform(get("/api/v1/workspace/views").with(staff.role("CASHIER")))
        .andExpect(jsonPath("$[*].name").value(hasItem(name)));
    mvc.perform(get("/api/v1/workspace/views").with(staff.role("SUPPORT")))
        .andExpect(jsonPath("$[*].name").value(not(hasItem(name))));

    String item =
        mvc.perform(
                post("/api/v1/workspace/items")
                    .with(staff.role("CASHIER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"В архив " + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String itemId = JsonPath.<String>read(item, "$.id");
    mvc.perform(post("/api/v1/workspace/items/{id}/archive", itemId).with(staff.role("CASHIER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").isNotEmpty());
    mvc.perform(get("/api/v1/workspace/items?archived=true").with(staff.role("CASHIER")))
        .andExpect(jsonPath("$[*].id").value(hasItem(itemId)));
    mvc.perform(post("/api/v1/workspace/items/{id}/restore", itemId).with(staff.role("CASHIER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").doesNotExist());

    mvc.perform(delete("/api/v1/workspace/views/{id}", viewId).with(staff.role("CASHIER")))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/api/v1/workspace/views/{id}", viewId).with(staff.role("CASHIER")))
        .andExpect(status().isNotFound());
  }
}
