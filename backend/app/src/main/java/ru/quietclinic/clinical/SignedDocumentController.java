package ru.quietclinic.clinical;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Архив подписанных документов: только GET, ничего не подписывает и не меняет. */
@RestController
@RequestMapping("/api/v1/clinical/signed-documents")
public class SignedDocumentController {
  private final SignedDocumentService documents;

  SignedDocumentController(SignedDocumentService documents) {
    this.documents = documents;
  }

  @GetMapping
  List<SignedDocumentView> list(@RequestParam(required = false) UUID practitionerId) {
    return documents.list(practitionerId);
  }

  @GetMapping("/{id}")
  SignedDocumentView get(@PathVariable UUID id) {
    return documents.get(id);
  }
}
