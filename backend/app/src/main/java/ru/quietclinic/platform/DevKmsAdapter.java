package ru.quietclinic.platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class DevKmsAdapter implements KmsPort {
  private final Map<String, byte[]> keys;
  private final byte[] indexKey;
  private final String currentVersion;

  DevKmsAdapter(
      @Value("${clinic.dev-kms-key:local-dev-key-change-me}") String source,
      @Value("${clinic.kms-key-version:dev-v1}") String currentVersion,
      @Value("${clinic.kms-keys:}") String additionalKeys) {
    this.currentVersion = currentVersion;
    keys = new HashMap<>();
    keys.put("dev-v1", derive(source));
    for (String entry : additionalKeys.split(",")) {
      if (entry.isBlank()) continue;
      int separator = entry.indexOf('=');
      if (separator < 1 || separator == entry.length() - 1) {
        throw new IllegalStateException("Each CLINIC_KMS_KEYS entry must be version=key");
      }
      String version = entry.substring(0, separator).trim();
      String key = entry.substring(separator + 1).trim();
      if (keys.putIfAbsent(version, derive(key)) != null) {
        throw new IllegalStateException("Duplicate KMS key version");
      }
    }
    if (!keys.containsKey(currentVersion)) {
      throw new IllegalStateException("Current KMS key version has no configured key");
    }
    // Ключ индекса привязан к исходному секрету и не меняется при ротации ключа шифрования.
    indexKey = derive("patient-contact-index\u0000" + source);
  }

  public byte[] dataKey() {
    return dataKey(currentVersion);
  }

  public byte[] dataKey(String version) {
    byte[] key = keys.get(version);
    if (key == null) throw new IllegalStateException("Unknown KMS key version");
    return key.clone();
  }

  public byte[] indexKey() {
    return indexKey.clone();
  }

  public String keyVersion() {
    return currentVersion;
  }

  private static byte[] derive(String source) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}
