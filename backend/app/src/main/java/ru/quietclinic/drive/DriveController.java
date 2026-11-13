package ru.quietclinic.drive;

import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Корпоративный диск (раздел F, drive). Файлы отдаются только как вложение. */
@RestController
@RequestMapping("/api/v1/drive")
public class DriveController {
  private final DriveService drive;

  DriveController(DriveService drive) {
    this.drive = drive;
  }

  /** Без parent — «Мой диск»; с parent — папка, путь и содержимое. */
  @GetMapping("/nodes")
  NodeDetails nodes(@RequestParam(required = false) UUID parent) {
    return parent == null ? drive.root() : drive.node(parent);
  }

  @GetMapping("/nodes/{id}")
  NodeDetails node(@PathVariable UUID id) {
    return drive.node(id);
  }

  @PutMapping("/nodes/{id}")
  NodeView update(@PathVariable UUID id, @Valid @RequestBody NodeUpdateRequest request) {
    return drive.update(id, request);
  }

  /** В корзину; окончательно — через clinic.drive.trash-days. */
  @DeleteMapping("/nodes/{id}")
  NodeView delete(@PathVariable UUID id) {
    return drive.delete(id);
  }

  @PostMapping("/nodes/{id}/restore")
  NodeView restore(@PathVariable UUID id) {
    return drive.restore(id);
  }

  @GetMapping("/shared-with-me")
  List<NodeView> sharedWithMe() {
    return drive.sharedWithMe();
  }

  @GetMapping("/trash")
  List<NodeView> trash() {
    return drive.trash();
  }

  @PostMapping("/folders")
  @ResponseStatus(HttpStatus.CREATED)
  NodeView createFolder(@Valid @RequestBody FolderRequest request) {
    return drive.createFolder(request);
  }

  @PostMapping(path = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  NodeView upload(
      @RequestParam("file") MultipartFile file, @RequestParam(required = false) UUID parentId)
      throws IOException {
    return drive.upload(
        parentId, file.getOriginalFilename(), file.getContentType(), file.getBytes());
  }

  @PostMapping(path = "/files/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  NodeView newVersion(@PathVariable UUID id, @RequestParam("file") MultipartFile file)
      throws IOException {
    return drive.newVersion(id, file.getContentType(), file.getBytes());
  }

  @GetMapping("/files/{id}/versions")
  List<VersionView> versions(@PathVariable UUID id) {
    return drive.versions(id);
  }

  @GetMapping("/files/{id}/content")
  ResponseEntity<byte[]> content(
      @PathVariable UUID id, @RequestParam(required = false) Integer version) {
    return attachment(drive.content(id, version));
  }

  @GetMapping("/files/{id}/preview")
  ResponseEntity<byte[]> preview(
      @PathVariable UUID id, @RequestParam(required = false) Integer version) {
    return inline(drive.preview(id, version));
  }

  @GetMapping("/nodes/{id}/shares")
  List<ShareView> shares(@PathVariable UUID id) {
    return drive.shares(id);
  }

  @PostMapping("/nodes/{id}/shares")
  @ResponseStatus(HttpStatus.CREATED)
  ShareView share(@PathVariable UUID id, @Valid @RequestBody ShareRequest request) {
    return drive.share(id, request);
  }

  @DeleteMapping("/nodes/{id}/shares/{shareId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void unshare(@PathVariable UUID id, @PathVariable UUID shareId) {
    drive.unshare(id, shareId);
  }

  /** Узел по ссылке — только после входа; node — вложенный узел папки, открытой по ссылке. */
  @GetMapping("/links/{token}")
  NodeDetails link(@PathVariable String token, @RequestParam(required = false) UUID node) {
    return drive.link(token, node);
  }

  @GetMapping("/links/{token}/content")
  ResponseEntity<byte[]> linkContent(
      @PathVariable String token, @RequestParam(required = false) UUID node) {
    return attachment(drive.linkContent(token, node));
  }

  @GetMapping("/links/{token}/preview")
  ResponseEntity<byte[]> linkPreview(
      @PathVariable String token, @RequestParam(required = false) UUID node) {
    return inline(drive.linkPreview(token, node));
  }

  @GetMapping("/principals")
  List<PrincipalView> principals() {
    return drive.principals();
  }

  @GetMapping("/quota")
  QuotaView quota() {
    return drive.quota();
  }

  @PutMapping("/quota")
  QuotaView setQuota(@Valid @RequestBody QuotaRequest request) {
    return drive.setQuota(request);
  }

  /** Всегда вложение и octet-stream: загруженный файл не исполняется в браузере как страница. */
  private static ResponseEntity<byte[]> attachment(FileContent file) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(file.name(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(file.bytes());
  }

  /** Только PDF и изображения из DriveService; HTML и SVG сюда не попадают. */
  private static ResponseEntity<byte[]> inline(FileContent file) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline()
                .filename(file.name(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(file.bytes());
  }
}
