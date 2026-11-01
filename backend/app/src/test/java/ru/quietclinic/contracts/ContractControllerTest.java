package ru.quietclinic.contracts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
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
import java.util.HexFormat;
import java.util.Map;
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
import ru.quietclinic.StaffSessions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ContractControllerTest {
  private static final String ANNA = "00000000-0000-0000-0000-000000000101";
  private static final String DOCX_MIME =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void createsImmutableEncryptedContractFromDocx() throws Exception {
    byte[] templateBytes =
        DocxTemplateTest.docx(
            Map.of(
                "word/document.xml",
                """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>
                {{contract.number}} | {{contract.date}} | {{patient.fullName}} | {{patient.phone}} |
                {{clinic.name}} | {{service.name}} | {{service.price}} | {{custom.note}}
                </w:t></w:r></w:p></w:body></w:document>
                """));
    String uploaded =
        mvc.perform(
                multipart("/api/v1/contract-templates")
                    .file(new MockMultipartFile("file", "договор.docx", DOCX_MIME, templateBytes))
                    .param("name", "Договор на приём")
                    .with(staff.admin()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.placeholders", hasItem("custom.note")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String templateId = JsonPath.read(uploaded, "$.id");
    String serviceId =
        jdbc.queryForObject(
            "select id from clinic_service where active = true order by name limit 1",
            String.class);
    String serviceName =
        jdbc.queryForObject(
            "select name from clinic_service where id = ?", String.class, serviceId);
    String endpoint = "/api/v1/patients/" + ANNA + "/contracts";

    mvc.perform(
            post(endpoint)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"templateId\":\"" + templateId + "\",\"serviceId\":\"" + serviceId + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONTRACT_VALUES_REQUIRED"));

    String created =
        mvc.perform(
                post(endpoint)
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"templateId\":\""
                            + templateId
                            + "\",\"serviceId\":\""
                            + serviceId
                            + "\",\"values\":{\"custom.note\":\"Особые условия & скидка\"}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.contractNumber").value(matchesPattern("Д-\\d{4}-\\d{5}")))
            .andExpect(jsonPath("$.serviceName").value(serviceName))
            .andExpect(jsonPath("$.pdfAvailable").value(false))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(created, "$.id");
    String number = JsonPath.read(created, "$.contractNumber");

    mvc.perform(get(endpoint).with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem(id)));
    mvc.perform(get(endpoint).with(staff.role("CASHIER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem(id)));
    mvc.perform(get("/api/v1/contract-patients").with(staff.role("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name", hasItem("Анна Михайлова")));
    byte[] downloaded =
        mvc.perform(get("/api/v1/patient-contracts/" + id + "/docx").with(staff.admin()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(DOCX_MIME))
            .andExpect(header().string("Content-Disposition", containsString("attachment")))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    String xml = DocxTemplateTest.entry(downloaded, "word/document.xml");
    assertThat(xml)
        .contains(
            number, "Анна Михайлова", "+79162451872", serviceName, "Особые условия &amp; скидка")
        .doesNotContain("{{");
    mvc.perform(get("/api/v1/patient-contracts/" + id + "/pdf").with(staff.admin()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CONTRACT_PDF_NOT_FOUND"));

    Map<String, Object> stored =
        jdbc.queryForMap(
            "select snapshot_values, docx_ciphertext, pdf_ciphertext, docx_hash from patient_contract where id = ?",
            UUID.fromString(id));
    assertThat((String) stored.get("snapshot_values"))
        .doesNotContain("Анна", "+79162451872", "Особые условия");
    byte[] ciphertext = (byte[]) stored.get("docx_ciphertext");
    assertThat(ciphertext).isNotEqualTo(downloaded);
    assertThat(new String(ciphertext, StandardCharsets.ISO_8859_1)).doesNotContain("{{", "Анна");
    assertThat(stored.get("pdf_ciphertext")).isNull();
    assertThat(stored.get("docx_hash"))
        .isEqualTo(
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(downloaded)));

    // Новая версия шаблона не меняет уже сформированный документ.
    mvc.perform(
            multipart("/api/v1/contract-templates")
                .file(new MockMultipartFile("file", "новая-версия.docx", DOCX_MIME, templateBytes))
                .param("name", "Договор на приём")
                .with(staff.admin()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(2));
    mvc.perform(get("/api/v1/patient-contracts/" + id + "/docx").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(content().bytes(downloaded));
  }
}
