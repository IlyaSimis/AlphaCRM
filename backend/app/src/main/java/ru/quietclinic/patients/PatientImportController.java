package ru.quietclinic.patients;

import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Безопасный перенос административных карточек из PostgreSQL: сначала preview, затем commit. */
@RestController
@RequestMapping("/api/v1/patients/import/postgresql")
public class PatientImportController {
  private final PatientImportService imports;

  PatientImportController(PatientImportService imports) {
    this.imports = imports;
  }

  @PostMapping("/preview")
  PreviewView preview(@RequestBody ImportRequest request) {
    return imports.preview(request);
  }

  @PostMapping("/commit")
  ImportResultView commit(@RequestBody ImportRequest request) {
    return imports.commit(request);
  }

  record ImportRequest(
      String url,
      String username,
      String password,
      String table,
      ColumnMapping columns,
      Integer limit) {}

  record ColumnMapping(
      String fullName, String birthDate, String phone, String secondPhone, String email) {}

  record PreviewView(
      int total, int created, int duplicates, int errors, List<ImportRowView> rows) {}

  record ImportRowView(
      int row,
      String fullName,
      String birthDate,
      String phone,
      String secondPhone,
      String email,
      String status,
      String reason,
      String existingPatientName) {}

  record ImportResultView(int total, int created, int duplicates, int errors) {}
}
