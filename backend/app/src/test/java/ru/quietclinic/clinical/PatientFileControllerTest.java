package ru.quietclinic.clinical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.quietclinic.SecondClinic;
import ru.quietclinic.StaffSessions;

/**
 * PDF пациента: шифрованное хранение, подпись без последующих изменений, права по ролям и связи с
 * лечением.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PatientFileControllerTest {
  static final String ANNA = "00000000-0000-0000-0000-000000000101";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired PatientFileRepository files;
  @Autowired PlatformTransactionManager transactions;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  private static byte[] pdf(String text) {
    return ("%PDF-1.4\n" + text + "\n%%EOF").getBytes(StandardCharsets.UTF_8);
  }

  private ResultActions upload(
      RequestPostProcessor who, String patientId, String name, byte[] bytes) throws Exception {
    return mvc.perform(
        multipart("/api/v1/patients/" + patientId + "/files")
            .file(new MockMultipartFile("file", name, "application/pdf", bytes))
            .with(who));
  }

  private String uploaded(RequestPostProcessor who, String patientId, byte[] bytes)
      throws Exception {
    var body =
        upload(who, patientId, "scan.pdf", bytes)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private String newPatient(String name, String phone) throws Exception {
    var body =
        mvc.perform(
                post("/api/v1/patients")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fullName":"%s","birthDate":"1980-01-01","phone":"%s",
                         "consentVersion":"v1.2","consentBasis":"SIGNED_FORM"}
                        """
                            .formatted(name, phone)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private String audit(String action, String fileId) {
    return jdbc.queryForObject(
            "select count(*) from audit_event where action = ? and object_id = ?"
                + " and object_type = 'PATIENT_FILE' and reason is null",
            Long.class,
            action,
            UUID.fromString(fileId))
        .toString();
  }

  @Test
  void storedEncryptedSignedOnceAndManagerGetsOnlySignedFiles() throws Exception {
    var bytes = pdf("Заключение для проверки шифрования");
    var body =
        upload(staff.admin(), ANNA, "C:\\scans\\2026\\заключение.pdf", bytes)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.originalName").value("заключение.pdf"))
            .andExpect(jsonPath("$.state").value("DRAFT"))
            .andExpect(jsonPath("$.sizeBytes").value(bytes.length))
            .andExpect(jsonPath("$.uploadedByName").value("Администратор"))
            .andExpect(jsonPath("$.contentHash").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(body, "$.id");

    // В столбце ciphertext нет открытых байтов файла.
    var stored =
        jdbc.queryForObject(
            "select ciphertext from patient_file where id = ?", byte[].class, UUID.fromString(id));
    var text = new String(stored, StandardCharsets.ISO_8859_1);
    assertThat(text).doesNotContain("%PDF-").doesNotContain("%%EOF");
    assertThat(indexOf(stored, "Заключение".getBytes(StandardCharsets.UTF_8))).isEqualTo(-1);
    assertThat(stored).isNotEqualTo(bytes);

    // Черновик менеджеру не отдаётся и в списке не виден; кассиру файлы закрыты совсем.
    mvc.perform(get("/api/v1/patient-files/" + id + "/content").with(staff.role("MANAGER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FILE_NOT_SIGNED"));
    mvc.perform(get("/api/v1/patients/" + ANNA + "/files").with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id").value(not(hasItem(id))));
    mvc.perform(get("/api/v1/patients/" + ANNA + "/files").with(staff.role("CASHIER")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/patient-files/" + id + "/content").with(staff.role("CASHIER")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/patient-files/" + id + "/sign").with(staff.role("MANAGER")))
        .andExpect(status().isForbidden());

    // Врач со связью с лечением подписывает; хеш — SHA-256 байтов файла.
    var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    mvc.perform(
            post("/api/v1/patient-files/" + id + "/sign").with(staff.doctor(StaffSessions.PETROV)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("SIGNED_LOCKED"))
        .andExpect(jsonPath("$.contentHash").value(hash))
        .andExpect(jsonPath("$.signedBy").value(staff.userId("t.petrov")))
        .andExpect(jsonPath("$.signedAt").exists());
    mvc.perform(post("/api/v1/patient-files/" + id + "/sign").with(staff.admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_SIGNED"));

    mvc.perform(get("/api/v1/patient-files/" + id + "/content").with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(header().string("Content-Disposition", containsString("attachment")))
        .andExpect(content().bytes(bytes));
    mvc.perform(get("/api/v1/patient-files/" + id + "/content").with(staff.role("CASHIER")))
        .andExpect(status().isForbidden());

    // Архив: менеджер — все подписанные файлы клиники, врач — подписанные им самим.
    mvc.perform(get("/api/v1/patient-files/signed").with(staff.role("MANAGER")))
        .andExpect(jsonPath("$[*].id").value(hasItem(id)));
    mvc.perform(get("/api/v1/patient-files/signed").with(staff.doctor(StaffSessions.PETROV)))
        .andExpect(jsonPath("$[*].id").value(hasItem(id)))
        .andExpect(
            jsonPath("$[?(@.id == '" + id + "')].patientName").value(hasItem("Анна Михайлова")));
    mvc.perform(get("/api/v1/patient-files/signed").with(staff.doctor(StaffSessions.LEBEDEVA)))
        .andExpect(jsonPath("$[*].id").value(not(hasItem(id))));
    mvc.perform(get("/api/v1/patient-files/signed").with(staff.role("CASHIER")))
        .andExpect(status().isForbidden());

    // В аудит — только id файла, без имени и содержимого.
    assertThat(audit("PDF_UPLOADED", id)).isEqualTo("1");
    assertThat(audit("PDF_SIGNED", id)).isEqualTo("1");
    assertThat(audit("PDF_READ", id)).isEqualTo("1");
  }

  @Test
  void onlyPdfUpToTenMegabytes() throws Exception {
    var before = count(ANNA);
    upload(
            staff.admin(),
            ANNA,
            "notes.pdf",
            "plain text, not a pdf".getBytes(StandardCharsets.UTF_8))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NOT_A_PDF"));
    var big = new byte[(int) PatientFileService.MAX_BYTES + 1];
    Arrays.fill(big, (byte) ' ');
    System.arraycopy(pdf(""), 0, big, 0, 9);
    upload(staff.admin(), ANNA, "big.pdf", big)
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    assertThat(count(ANNA)).isEqualTo(before);
    // Ровно 10 МБ — ещё можно.
    var limit = Arrays.copyOf(big, (int) PatientFileService.MAX_BYTES);
    upload(staff.admin(), ANNA, "limit.pdf", limit).andExpect(status().isCreated());
  }

  @Test
  void doctorWithoutCareRelationshipNeitherUploadsNorSignsNorReads() throws Exception {
    var patient = newPatient("PDF Без Связи", "+70000000401");
    upload(staff.doctor(StaffSessions.PETROV), patient, "scan.pdf", pdf("без связи"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NO_CARE_RELATIONSHIP"));
    var id = uploaded(staff.admin(), patient, pdf("от администратора"));
    mvc.perform(
            post("/api/v1/patient-files/" + id + "/sign").with(staff.doctor(StaffSessions.PETROV)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("NO_CARE_RELATIONSHIP"));
    mvc.perform(
            get("/api/v1/patient-files/" + id + "/content")
                .with(staff.doctor(StaffSessions.PETROV)))
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/patients/" + patient + "/files").with(staff.doctor(StaffSessions.PETROV)))
        .andExpect(status().isForbidden());
    assertThat(
            jdbc.queryForObject(
                "select state from patient_file where id = ?", String.class, UUID.fromString(id)))
        .isEqualTo("DRAFT");
    // Администратор подписывает любой файл своей клиники.
    mvc.perform(post("/api/v1/patient-files/" + id + "/sign").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.signedByName").value("Администратор"));
  }

  @Test
  void anotherClinicSeesNeitherThePatientNorTheFile() throws Exception {
    var id = uploaded(staff.admin(), ANNA, pdf("своя клиника"));
    SecondClinic.ensure(jdbc);
    var other = staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    mvc.perform(get("/api/v1/patients/" + ANNA + "/files").with(other))
        .andExpect(status().isNotFound());
    upload(other, ANNA, "scan.pdf", pdf("чужая")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/patient-files/" + id + "/content").with(other))
        .andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/patient-files/" + id + "/sign").with(other))
        .andExpect(status().isNotFound());
  }

  @Test
  void signedBytesCannotBeReplaced() throws Exception {
    var original = pdf("подписанный оригинал");
    var id = uploaded(staff.admin(), ANNA, original);
    mvc.perform(post("/api/v1/patient-files/" + id + "/sign").with(staff.admin()))
        .andExpect(status().isOk());
    var tx = new TransactionTemplate(transactions);
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    status -> {
                      var file = files.findById(UUID.fromString(id)).orElseThrow();
                      file.ciphertext = new byte[] {1, 2, 3};
                      file.originalName = "replaced.pdf";
                      files.flush();
                    }))
        .hasStackTraceContaining("Signed file is immutable");
    mvc.perform(get("/api/v1/patient-files/" + id + "/content").with(staff.admin()))
        .andExpect(content().bytes(original));
    // Новый файл — новая строка, подписанный остаётся как был.
    var replacement = uploaded(staff.admin(), ANNA, pdf("новая версия"));
    assertThat(replacement).isNotEqualTo(id);
  }

  private long count(String patientId) {
    return jdbc.queryForObject(
        "select count(*) from patient_file where patient_id = ?",
        Long.class,
        UUID.fromString(patientId));
  }

  private static int indexOf(byte[] haystack, byte[] needle) {
    outer:
    for (var i = 0; i <= haystack.length - needle.length; i++) {
      for (var j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) continue outer;
      }
      return i;
    }
    return -1;
  }
}
