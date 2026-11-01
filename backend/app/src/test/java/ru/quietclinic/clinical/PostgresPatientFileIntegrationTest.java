package ru.quietclinic.clinical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.quietclinic.StaffSessions;

/**
 * На PostgreSQL подписанный PDF не меняется и не удаляется даже прямым SQL — это делает триггер.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PostgresPatientFileIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:18-alpine")
          .withDatabaseName("quiet_clinic")
          .withUsername("quiet_clinic")
          .withPassword("quiet_clinic");

  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("clinic.admin-password", () -> "admin");
    registry.add("clinic.admin-must-change-password", () -> "false");
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void signedFileIsImmutableInTheDatabase() throws Exception {
    byte[] bytes = "%PDF-1.4\nсодержимое на PostgreSQL\n%%EOF".getBytes(StandardCharsets.UTF_8);
    String body =
        mvc.perform(
                multipart("/api/v1/patients/00000000-0000-0000-0000-000000000101/files")
                    .file(new MockMultipartFile("file", "scan.pdf", "application/pdf", bytes))
                    .with(staff.admin()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
    byte[] stored =
        jdbc.queryForObject("select ciphertext from patient_file where id = ?", byte[].class, id);
    assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("%PDF-");

    // Черновик ещё можно подписать — это обычный UPDATE.
    mvc.perform(post("/api/v1/patient-files/" + id + "/sign").with(staff.admin()))
        .andExpect(status().isOk());

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update patient_file set ciphertext = ? where id = ?", new byte[] {1, 2}, id))
        .isInstanceOf(DataAccessException.class)
        .hasStackTraceContaining("immutable");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update patient_file set original_name = 'other.pdf', content_hash = 'x'"
                        + " where id = ?",
                    id))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () -> jdbc.update("update patient_file set state = 'DRAFT' where id = ?", id))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> jdbc.update("delete from patient_file where id = ?", id))
        .isInstanceOf(DataAccessException.class);
    assertThat(
            jdbc.queryForObject(
                "select ciphertext from patient_file where id = ?", byte[].class, id))
        .isEqualTo(stored);
  }
}
