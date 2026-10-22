package ru.quietclinic.platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class DevKmsAdapter implements KmsPort {
  private final byte[] key;

  DevKmsAdapter(@Value("${clinic.dev-kms-key:local-dev-key-change-me}") String source) {
    try {
      key = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  public byte[] dataKey() {
    return key.clone();
  }

  public String keyVersion() {
    return "dev-v1";
  }
}
