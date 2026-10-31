package ru.quietclinic.contracts;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Локальный конвертер договоров и временного предпросмотра офисных документов. */
@Component
public class LibreOfficePdfConverter {
  private final String binary;

  LibreOfficePdfConverter(@Value("${clinic.contracts.libreoffice-binary:}") String binary) {
    this.binary = binary == null ? "" : binary.trim();
  }

  boolean available() {
    return !binary.isEmpty();
  }

  Optional<byte[]> convert(byte[] docx) {
    return convert(docx, "docx");
  }

  /** Конвертация только в памяти запроса: временный каталог удаляется сразу после чтения PDF. */
  public Optional<byte[]> convert(byte[] document, String extension) {
    if (!available()) return Optional.empty();
    if (!extension.equals("docx") && !extension.equals("xlsx")) {
      throw conflict("PREVIEW_UNSUPPORTED", "This file format cannot be previewed");
    }
    Path directory = null;
    try {
      directory = Files.createTempDirectory("alphacrm-document-");
      var input = directory.resolve("document." + extension);
      Files.write(input, document);
      var profile = directory.resolve("lo-profile");
      var process =
          new ProcessBuilder(
                  binary,
                  "--headless",
                  "--nologo",
                  "--nodefault",
                  "--nolockcheck",
                  "-env:UserInstallation=" + profile.toUri(),
                  "--convert-to",
                  "pdf",
                  "--outdir",
                  directory.toString(),
                  input.toString())
              .redirectErrorStream(true)
              .start();
      if (!process.waitFor(45, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw conflict("PDF_CONVERSION_TIMEOUT", "PDF conversion timed out");
      }
      if (process.exitValue() != 0) {
        throw conflict("PDF_CONVERSION_FAILED", "Cannot convert the document to PDF");
      }
      var pdf = directory.resolve("document.pdf");
      if (!Files.isRegularFile(pdf)) {
        throw conflict("PDF_CONVERSION_FAILED", "Cannot convert the document to PDF");
      }
      return Optional.of(Files.readAllBytes(pdf));
    } catch (java.io.IOException ex) {
      throw conflict("PDF_CONVERSION_FAILED", "Cannot convert the contract to PDF");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw conflict("PDF_CONVERSION_FAILED", "PDF conversion was interrupted");
    } finally {
      if (directory != null) {
        try (var paths = Files.walk(directory)) {
          paths
              .sorted(Comparator.reverseOrder())
              .forEach(
                  path -> {
                    try {
                      Files.deleteIfExists(path);
                    } catch (java.io.IOException ignored) {
                      // Временный каталог будет очищен ОС; пути и содержимое не попадают в ответ.
                    }
                  });
        } catch (java.io.IOException ignored) {
          // Ничего не делаем: результат договора уже сформирован.
        }
      }
    }
  }
}
