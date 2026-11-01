package ru.quietclinic.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.quietclinic.SecondClinic;
import ru.quietclinic.StaffSessions;
import ru.quietclinic.contracts.LibreOfficePdfConverter;

/** Корпоративный диск (раздел H): права с наследованием, версии, ссылки, корзина, квота. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DriveControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean LibreOfficePdfConverter pdf;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  private String read(org.springframework.test.web.servlet.ResultActions actions, String path)
      throws Exception {
    return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), path).toString();
  }

  private String folder(String name) throws Exception {
    return read(
        mvc.perform(
                post("/api/v1/drive/folders")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated()),
        "$.id");
  }

  private MockMultipartFile file(String name, String text) {
    return new MockMultipartFile("file", name, "text/plain", text.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void sharedFolderIsReadableByRoleAndOnlyOwnerManagesIt() throws Exception {
    var folder = folder("Regulations " + UUID.randomUUID());
    var fileId =
        read(
            mvc.perform(
                    multipart("/api/v1/drive/files")
                        .file(file("rules.txt", "first"))
                        .param("parentId", folder)
                        .with(staff.admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentVersion").value(1)),
            "$.id");
    mvc.perform(
            multipart("/api/v1/drive/files/" + fileId + "/versions")
                .file(file("rules.txt", "second"))
                .with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentVersion").value(2));

    // Без права чужой узел выглядит как несуществующий.
    mvc.perform(get("/api/v1/drive/nodes").param("parent", folder).with(staff.role("NURSE")))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/drive/nodes/" + folder + "/shares")
                .with(staff.role("NURSE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"principalType\":\"CLINIC\",\"permission\":\"VIEW\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/drive/nodes/" + folder + "/shares")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"principalType\":\"ROLE\",\"principalId\":\"NURSE\",\"permission\":\"VIEW\"}"))
        .andExpect(status().isCreated());

    // Право на папку наследует файл внутри.
    mvc.perform(get("/api/v1/drive/nodes").param("parent", folder).with(staff.role("NURSE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.children[0].permission").value("VIEW"));
    mvc.perform(get("/api/v1/drive/shared-with-me").with(staff.role("NURSE")))
        .andExpect(jsonPath("$[*].id").value(hasItem(folder)));
    mvc.perform(get("/api/v1/drive/files/" + fileId + "/content").with(staff.role("NURSE")))
        .andExpect(status().isOk())
        .andExpect(content().string("second"));
    mvc.perform(
            get("/api/v1/drive/files/" + fileId + "/content")
                .param("version", "1")
                .with(staff.role("NURSE")))
        .andExpect(content().string("first"));
    mvc.perform(
            multipart("/api/v1/drive/files")
                .file(file("nurse.txt", "x"))
                .param("parentId", folder)
                .with(staff.role("NURSE")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DRIVE_FORBIDDEN"));
    mvc.perform(delete("/api/v1/drive/nodes/" + fileId).with(staff.role("NURSE")))
        .andExpect(status().isForbidden());
    // Другой роли папка по-прежнему закрыта.
    mvc.perform(get("/api/v1/drive/files/" + fileId + "/content").with(staff.role("CASHIER")))
        .andExpect(status().isNotFound());

    // Чтение — в журнале, без имени файла.
    var reads =
        jdbc.queryForObject(
            "select count(*) from audit_event where action = 'DRIVE_FILE_READ' and object_id = ?"
                + " and (reason is null or reason not like '%rules%')",
            Long.class, UUID.fromString(fileId));
    assertThat(reads).isGreaterThanOrEqualTo(2L);
  }

  @Test
  void linkWorksOnlyAfterSignInAndTrashHidesContent() throws Exception {
    var folder = folder("Links " + UUID.randomUUID());
    var fileId =
        read(
            mvc.perform(
                    multipart("/api/v1/drive/files")
                        .file(file("link.txt", "by link"))
                        .param("parentId", folder)
                        .with(staff.admin()))
                .andExpect(status().isCreated()),
            "$.id");
    mvc.perform(
            post("/api/v1/drive/nodes/" + fileId + "/shares")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"principalType\":\"LINK\",\"permission\":\"EDIT\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DRIVE_LINK_VIEW_ONLY"));
    var token =
        read(
            mvc.perform(
                    post("/api/v1/drive/nodes/" + fileId + "/shares")
                        .with(staff.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalType\":\"LINK\",\"permission\":\"VIEW\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty()),
            "$.token");
    // Токен хранится только хешем.
    var raw =
        jdbc.queryForObject(
            "select count(*) from drive.drive_share where link_token_hash = ?", Long.class, token);
    assertThat(raw).isZero();

    mvc.perform(get("/api/v1/drive/links/" + token + "/content").with(staff.anonymous()))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/drive/links/" + token + "/content").with(staff.role("CASHIER")))
        .andExpect(status().isOk())
        .andExpect(content().string("by link"));
    SecondClinic.ensure(jdbc);
    var other = staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    mvc.perform(get("/api/v1/drive/links/" + token).with(other)).andExpect(status().isNotFound());

    mvc.perform(delete("/api/v1/drive/nodes/" + folder).with(staff.admin()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/drive/trash").with(staff.admin()))
        .andExpect(jsonPath("$[*].id").value(hasItem(folder)));
    mvc.perform(get("/api/v1/drive/links/" + token + "/content").with(staff.role("CASHIER")))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/drive/nodes/" + folder + "/restore").with(staff.admin()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/drive/nodes").with(staff.admin()))
        .andExpect(jsonPath("$.children[*].id").value(hasItem(folder)));
  }

  @Test
  void previewUsesTheSameRightsAndConvertsOfficeDocumentsOnlyForViewing() throws Exception {
    var suffix = UUID.randomUUID();
    var pdfBytes = "%PDF-1.4\npreview\n%%EOF".getBytes(StandardCharsets.UTF_8);
    var pdfId =
        read(
            mvc.perform(
                    multipart("/api/v1/drive/files")
                        .file(
                            new MockMultipartFile(
                                "file", "preview-" + suffix + ".pdf", "application/pdf", pdfBytes))
                        .with(staff.admin()))
                .andExpect(status().isCreated()),
            "$.id");

    mvc.perform(get("/api/v1/drive/files/" + pdfId + "/preview").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(content().bytes(pdfBytes));
    mvc.perform(get("/api/v1/drive/files/" + pdfId + "/preview").with(staff.role("NURSE")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/drive/files/" + pdfId + "/preview").with(staff.anonymous()))
        .andExpect(status().isUnauthorized());

    var imageBytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47};
    var imageId =
        read(
            mvc.perform(
                    multipart("/api/v1/drive/files")
                        .file(
                            new MockMultipartFile(
                                "file", "image-" + suffix + ".png", "image/png", imageBytes))
                        .with(staff.admin()))
                .andExpect(status().isCreated()),
            "$.id");
    mvc.perform(get("/api/v1/drive/files/" + imageId + "/preview").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(content().bytes(imageBytes));

    when(pdf.convert(any(byte[].class), anyString()))
        .thenAnswer(
            invocation ->
                Optional.of(
                    ("%PDF-" + invocation.getArgument(1)).getBytes(StandardCharsets.UTF_8)));
    for (var extension : new String[] {"docx", "xlsx"}) {
      var officeId =
          read(
              mvc.perform(
                      multipart("/api/v1/drive/files")
                          .file(
                              new MockMultipartFile(
                                  "file",
                                  "office-" + suffix + "." + extension,
                                  "application/octet-stream",
                                  new byte[] {1, 2, 3}))
                          .with(staff.admin()))
                  .andExpect(status().isCreated()),
              "$.id");
      mvc.perform(get("/api/v1/drive/files/" + officeId + "/preview").with(staff.admin()))
          .andExpect(status().isOk())
          .andExpect(content().contentType(MediaType.APPLICATION_PDF))
          .andExpect(content().bytes(("%PDF-" + extension).getBytes(StandardCharsets.UTF_8)));
    }

    var textId =
        read(
            mvc.perform(
                    multipart("/api/v1/drive/files")
                        .file(file("text-" + suffix + ".txt", "not previewable"))
                        .with(staff.admin()))
                .andExpect(status().isCreated()),
            "$.id");
    mvc.perform(get("/api/v1/drive/files/" + textId + "/preview").with(staff.admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PREVIEW_UNSUPPORTED"));

    var token =
        read(
            mvc.perform(
                    post("/api/v1/drive/nodes/" + pdfId + "/shares")
                        .with(staff.admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalType\":\"LINK\",\"permission\":\"VIEW\"}"))
                .andExpect(status().isCreated()),
            "$.token");
    mvc.perform(
            get("/api/v1/drive/links/" + token + "/preview")
                .param("node", pdfId)
                .with(staff.role("CASHIER")))
        .andExpect(status().isOk())
        .andExpect(content().bytes(pdfBytes));
  }

  @Test
  void namesAreUniqueInFolderAndQuotaIsAdministered() throws Exception {
    var name = "Unique " + UUID.randomUUID();
    folder(name);
    mvc.perform(
            post("/api/v1/drive/folders")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name.toUpperCase() + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DRIVE_NAME_TAKEN"));

    mvc.perform(get("/api/v1/drive/quota").with(staff.role("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxFileBytes").isNumber());
    mvc.perform(
            put("/api/v1/drive/quota")
                .with(staff.role("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bytesLimit\":1000}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/v1/drive/quota")
                .with(staff.role("SUPPORT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bytesLimit\":10737418240}"))
        .andExpect(status().isOk());
    // Техподдержка управляет квотой, но чужих файлов не видит.
    mvc.perform(get("/api/v1/drive/nodes").with(staff.role("SUPPORT")))
        .andExpect(jsonPath("$.children[*].name").value(not(hasItem(name))));
  }
}
