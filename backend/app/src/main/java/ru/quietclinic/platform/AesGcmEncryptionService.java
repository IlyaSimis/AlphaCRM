package ru.quietclinic.platform;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
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
    if (plaintext == null) return null;
    try {
      var nonce = new byte[12];
      random.nextBytes(nonce);
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(kms.dataKey(), "AES"),
          new GCMParameterSpec(128, nonce));
      var encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
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
    if (ciphertext == null) return null;
    try {
      var encoded = ciphertext.substring(ciphertext.indexOf('.') + 1);
      var packed = Base64.getUrlDecoder().decode(encoded);
      var nonce = java.util.Arrays.copyOfRange(packed, 0, 12);
      var encrypted = java.util.Arrays.copyOfRange(packed, 12, packed.length);
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(kms.dataKey(), "AES"),
          new GCMParameterSpec(128, nonce));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      throw new IllegalStateException("Decryption failed", ex);
    }
  }

  public byte[] encryptBytes(byte[] plaintext) {
    if (plaintext == null) return null;
    try {
      var version = kms.keyVersion().getBytes(StandardCharsets.UTF_8);
      if (version.length > 255) throw new IllegalStateException("Key version is too long");
      var nonce = new byte[12];
      random.nextBytes(nonce);
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(kms.dataKey(), "AES"),
          new GCMParameterSpec(128, nonce));
      var encrypted = cipher.doFinal(plaintext);
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
      var offset = 1 + (ciphertext[0] & 0xff);
      var nonce = java.util.Arrays.copyOfRange(ciphertext, offset, offset + 12);
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(kms.dataKey(), "AES"),
          new GCMParameterSpec(128, nonce));
      return cipher.doFinal(ciphertext, offset + 12, ciphertext.length - offset - 12);
    } catch (Exception ex) {
      throw new IllegalStateException("Decryption failed", ex);
    }
  }
}
