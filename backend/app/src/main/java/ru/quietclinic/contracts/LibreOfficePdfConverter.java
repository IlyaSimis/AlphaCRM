package ru.quietclinic.contracts;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Необязательный локальный конвертер. Без настроенного бинарника DOCX остаётся доступным. */
@Component
class LibreOfficePdfConverter {
  private final String binary;

  LibreOfficePdfConverter(@Value("${clinic.contracts.libreoffice-binary:}") String binary) {
    this.binary = binary == null ? "" : binary.trim();
  }

  boolean available() {
    return !binary.isEmpty();
  }

  Optional<byte[]> convert(byte[] docx) {
    if (!available()) return Optional.empty();
    Path directory = null;
    try {
      directory = Files.createTempDirectory("alphacrm-contract-");
      var input = directory.resolve("contract.docx");
      Files.write(input, docx);
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
        throw conflict("PDF_CONVERSION_FAILED", "Cannot convert the contract to PDF");
      }
      var pdf = directory.resolve("contract.pdf");
      if (!Files.isRegularFile(pdf)) {
        throw conflict("PDF_CONVERSION_FAILED", "Cannot convert the contract to PDF");
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
