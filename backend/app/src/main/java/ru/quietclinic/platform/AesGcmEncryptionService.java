package ru.quietclinic.platform;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
class AesGcmEncryptionService implements EncryptionService {
  private final KmsPort kms;
  private final SecureRandom random = new SecureRandom();

  AesGcmEncryptionService(KmsPort kms) {
    this.kms = kms;
  }

  public String encrypt(String plaintext) {
    return encrypt(plaintext, null);
  }

  public String encrypt(String plaintext, String aad) {
    if (plaintext == null) return null;
    try {
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(kms.dataKey(), "AES"),
          new GCMParameterSpec(128, nonce));
      updateAad(cipher, aad);
      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      return kms.keyVersion()
          + "."
          + Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(
                  ByteBuffer.allocate(nonce.length + encrypted.length)
                      .put(nonce)
                      .put(encrypted)
                      .array());
    } catch (Exception ex) {
      throw new IllegalStateException("Encryption failed", ex);
    }
  }

  public String decrypt(String ciphertext) {
    return decrypt(ciphertext, null);
  }

  public String decrypt(String ciphertext, String aad) {
    if (ciphertext == null) return null;
    try {
      int separator = ciphertext.indexOf('.');
      if (separator < 1) throw new IllegalArgumentException("Missing key version");
      String version = ciphertext.substring(0, separator);
      byte[] packed = Base64.getUrlDecoder().decode(ciphertext.substring(separator + 1));
      byte[] nonce = Arrays.copyOfRange(packed, 0, 12);
      byte[] encrypted = Arrays.copyOfRange(packed, 12, packed.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(kms.dataKey(version), "AES"),
          new GCMParameterSpec(128, nonce));
      updateAad(cipher, aad);
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new IllegalStateException("Decryption failed", ex);
    }
  }

  public byte[] encryptBytes(byte[] plaintext) {
    if (plaintext == null) return null;
    try {
      byte[] version = kms.keyVersion().getBytes(StandardCharsets.UTF_8);
      if (version.length > 255) throw new IllegalStateException("Key version is too long");
      byte[] nonce = new byte[12];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(kms.dataKey(), "AES"),
          new GCMParameterSpec(128, nonce));
      byte[] encrypted = cipher.doFinal(plaintext);
      return ByteBuffer.allocate(1 + version.length + nonce.length + encrypted.length)
          .put((byte) version.length)
          .put(version)
          .put(nonce)
          .put(encrypted)
          .array();
    } catch (IllegalStateException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("Encryption failed", ex);
    }
  }

  public byte[] decryptBytes(byte[] ciphertext) {
    if (ciphertext == null) return null;
    try {
      int versionLength = ciphertext[0] & 0xff;
      String version = new String(ciphertext, 1, versionLength, StandardCharsets.UTF_8);
      int offset = 1 + versionLength;
      byte[] nonce = Arrays.copyOfRange(ciphertext, offset, offset + 12);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(kms.dataKey(version), "AES"),
          new GCMParameterSpec(128, nonce));
      return cipher.doFinal(ciphertext, offset + 12, ciphertext.length - offset - 12);
    } catch (Exception ex) {
      throw new IllegalStateException("Decryption failed", ex);
    }
  }

  public String blindIndex(String purpose, String normalizedValue) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(kms.indexKey(), "HmacSHA256"));
      mac.update(purpose.getBytes(StandardCharsets.UTF_8));
      mac.update((byte) 0);
      return HexFormat.of()
          .formatHex(mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("Blind index failed", ex);
    }
  }

  private static void updateAad(Cipher cipher, String aad) {
    if (aad != null) cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
  }
}
