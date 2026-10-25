package ru.quietclinic.clinical;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** PDF пациента. Права и правила — в {@link PatientFileService}; публичных ссылок нет. */
@RestController
@RequestMapping("/api/v1")
public class PatientFileController {
  private final PatientFileService files;

  PatientFileController(PatientFileService files) {
    this.files = files;
  }

  @GetMapping("/patients/{patientId}/files")
  List<PatientFileView> list(@PathVariable UUID patientId) {
    return files.list(patientId);
  }

  @PostMapping(path = "/patients/{patientId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  PatientFileView upload(
      @PathVariable UUID patientId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false) UUID appointmentId)
      throws IOException {
    return files.upload(patientId, appointmentId, file.getOriginalFilename(), file.getBytes());
  }

  @PostMapping("/patient-files/{id}/sign")
  PatientFileView sign(@PathVariable UUID id) {
    return files.sign(id);
  }

  /** Расшифрованные байты как вложение: файл скачивается, а не открывается по ссылке. */
  @GetMapping("/patient-files/{id}/content")
  ResponseEntity<byte[]> download(@PathVariable UUID id) {
    var file = files.download(id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(file.originalName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(file.content());
  }

  @GetMapping("/patient-files/signed")
  List<PatientFileView> signed() {
    return files.signedArchive();
  }
}
