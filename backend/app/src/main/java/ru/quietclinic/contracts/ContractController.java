package ru.quietclinic.contracts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Административные договоры: шаблоны, формирование и скачивание по сессии сотрудника. */
@RestController
@RequestMapping("/api/v1")
public class ContractController {
  private final ContractService contracts;

  ContractController(ContractService contracts) {
    this.contracts = contracts;
  }

  @GetMapping("/contract-templates")
  List<TemplateView> templates() {
    return contracts.templates();
  }

  @PostMapping(path = "/contract-templates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  TemplateView upload(@RequestParam String name, @RequestParam("file") MultipartFile file)
      throws IOException {
    return contracts.uploadTemplate(name, file.getOriginalFilename(), file.getBytes());
  }

  @GetMapping("/patients/{patientId}/contracts")
  List<ContractView> list(@PathVariable UUID patientId) {
    return contracts.list(patientId);
  }

  @PostMapping("/patients/{patientId}/contracts")
  @ResponseStatus(HttpStatus.CREATED)
  ContractView create(
      @PathVariable UUID patientId, @Valid @RequestBody CreateContractRequest request) {
    return contracts.create(patientId, request);
  }

  @GetMapping("/patient-contracts/{id}/docx")
  ResponseEntity<byte[]> docx(@PathVariable UUID id) {
    return download(contracts.download(id, false));
  }

  @GetMapping("/patient-contracts/{id}/pdf")
  ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
    return download(contracts.download(id, true));
  }

  private static ResponseEntity<byte[]> download(ContractService.ContractFile file) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.mediaType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(file.content());
  }

  record TemplateView(
      UUID id,
      String name,
      String originalName,
      int version,
      List<String> placeholders,
      Instant createdAt) {}

  record CreateContractRequest(
      @NotNull UUID templateId, UUID serviceId, Map<String, String> values) {}

  record ContractView(
      UUID id,
      UUID patientId,
      String contractNumber,
      String templateName,
      String serviceName,
      boolean pdfAvailable,
      Instant createdAt,
      String createdByName) {}
}
