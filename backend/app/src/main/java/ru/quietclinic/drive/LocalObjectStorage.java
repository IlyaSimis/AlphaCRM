package ru.quietclinic.drive;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Объекты — файлы в каталоге clinic.drive.storage-dir; запись атомарна (временный файл + move). */
@Component
class LocalObjectStorage implements ObjectStorage {
  private final Path root;

  LocalObjectStorage(@Value("${clinic.drive.storage-dir:./data/drive}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  private Path path(String key) {
    if (!key.matches("[0-9a-f-]{36}/[0-9a-f-]{36}")) {
      throw new IllegalArgumentException("Invalid object key");
    }
    return root.resolve(key).normalize();
  }

  @Override
  public void put(String key, byte[] content) {
    try {
      Path target = path(key);
      Files.createDirectories(target.getParent());
      Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
      Files.write(temp, content);
      Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  @Override
  public byte[] get(String key) {
    try {
      return Files.readAllBytes(path(key));
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  @Override
  public void delete(String key) {
    try {
      Files.deleteIfExists(path(key));
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
