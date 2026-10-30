package ru.quietclinic.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AesGcmEncryptionServiceTest {
  private static final String OLD_KEY = "old-key-0123456789abcdef0123456789abcdef";
  private static final String NEW_KEY = "new-key-0123456789abcdef0123456789abcdef";

  @Test
  void aadPreventsMovingCiphertextToAnotherEntity() {
    var service = new AesGcmEncryptionService(new DevKmsAdapter(OLD_KEY, "dev-v1", ""));
    var encrypted = service.encrypt("123-456-789 01", "patient:one:profile");

    assertThat(service.decrypt(encrypted, "patient:one:profile")).isEqualTo("123-456-789 01");
    assertThatThrownBy(() -> service.decrypt(encrypted, "patient:two:profile"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rotationKeepsOldTextAndBinaryRecordsReadable() {
    var oldService = new AesGcmEncryptionService(new DevKmsAdapter(OLD_KEY, "dev-v1", ""));
    var oldText = oldService.encrypt("clinical note");
    var oldBytes = oldService.encryptBytes("%PDF-old".getBytes(StandardCharsets.UTF_8));
    var rotated = new AesGcmEncryptionService(new DevKmsAdapter(OLD_KEY, "v2", "v2=" + NEW_KEY));

    assertThat(rotated.decrypt(oldText)).isEqualTo("clinical note");
    assertThat(rotated.decryptBytes(oldBytes))
        .isEqualTo("%PDF-old".getBytes(StandardCharsets.UTF_8));
    var current = rotated.encrypt("new note");
    assertThat(current).startsWith("v2.");
    assertThat(rotated.decrypt(current)).isEqualTo("new note");
  }

  @Test
  void blindIndexIsDeterministicButDoesNotContainPhone() {
    var service = new AesGcmEncryptionService(new DevKmsAdapter(OLD_KEY, "dev-v1", ""));

    var first = service.blindIndex("phone-prefix", "7916");
    var second = service.blindIndex("phone-prefix", "7916");

    assertThat(first).isEqualTo(second).hasSize(64).doesNotContain("7916");
    assertThat(service.blindIndex("phone-prefix", "7917")).isNotEqualTo(first);
  }
}
