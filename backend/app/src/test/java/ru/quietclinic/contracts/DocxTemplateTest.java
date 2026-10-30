package ru.quietclinic.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class DocxTemplateTest {
  @Test
  void replacesVariablesSplitAcrossRunsAndPreservesOtherMarkup() throws Exception {
    var docx =
        docx(
            Map.of(
                "word/document.xml",
                """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>
                <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Пациент: {{patient.full</w:t></w:r>
                <w:r><w:t>Name}}. Условия сохранены.</w:t></w:r></w:p></w:body></w:document>
                """,
                "word/header1.xml",
                """
                <w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:p><w:r><w:t>Договор {{contract.number}}</w:t></w:r></w:p></w:hdr>
                """));

    assertThat(DocxTemplate.placeholders(docx))
        .containsExactlyInAnyOrder("patient.fullName", "contract.number");
    var rendered =
        DocxTemplate.render(
            docx,
            Map.of("patient.fullName", "Анна <Михайлова>", "contract.number", "Д-2026-00001"));
    var document = entry(rendered, "word/document.xml");
    assertThat(document)
        .contains("Пациент: Анна &lt;Михайлова&gt;", ". Условия сохранены.")
        .contains("<w:rPr><w:b/></w:rPr>")
        .doesNotContain("{{patient");
    assertThat(entry(rendered, "word/header1.xml")).contains("Договор Д-2026-00001");
  }

  static byte[] docx(Map<String, String> xmlParts) throws Exception {
    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      for (var part : xmlParts.entrySet()) {
        zip.putNextEntry(new ZipEntry(part.getKey()));
        zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    return output.toByteArray();
  }

  static String entry(byte[] docx, String name) throws Exception {
    try (var zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (name.equals(entry.getName()))
          return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
      }
    }
    throw new AssertionError("DOCX entry not found: " + name);
  }
}
