package ru.quietclinic.staffchat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Файлы чата лежат отдельно от БД и корпоративного диска; ключи генерирует только сервер. */
@Component
class StaffChatAttachmentStorage {
  private final Path root;

  StaffChatAttachmentStorage(@Value("${clinic.chat.storage-dir:./data/staff-chat}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  void put(String key, byte[] content) {
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

  byte[] get(String key) {
    try {
      return Files.readAllBytes(path(key));
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private Path path(String key) {
    if (!key.matches("[0-9a-f-]{36}/[0-9a-f-]{36}")) {
      throw new IllegalArgumentException("Invalid attachment key");
    }
    return root.resolve(key).normalize();
  }
}
