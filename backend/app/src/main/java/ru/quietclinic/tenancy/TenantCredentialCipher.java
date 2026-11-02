package ru.quietclinic.tenancy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import ru.quietclinic.platform.KmsPort;

/** Envelope-шифрование пароля tenant БД; в каталоге не остаётся открытого секрета. */
@Component
class TenantCredentialCipher {
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private final KmsPort kms;
  private final SecureRandom random = new SecureRandom();

  TenantCredentialCipher(KmsPort kms) {
    this.kms = kms;
  }

  Sealed seal(UUID tenantId, String password) {
    byte[] dataKey = new byte[32];
    byte[] masterKey = new byte[0];
    byte[] plaintext = password.getBytes(StandardCharsets.UTF_8);
    try {
      random.nextBytes(dataKey);
      masterKey = kms.dataKey();
      String aad = aad(tenantId);
      return new Sealed(
          crypt(Cipher.ENCRYPT_MODE, dataKey, plaintext, aad),
          crypt(Cipher.ENCRYPT_MODE, masterKey, dataKey, "wrap:" + aad),
          kms.keyVersion());
    } catch (GeneralSecurityException error) {
      throw new IllegalStateException("Tenant credential encryption failed", error);
    } finally {
      Arrays.fill(dataKey, (byte) 0);
      Arrays.fill(masterKey, (byte) 0);
      Arrays.fill(plaintext, (byte) 0);
    }
  }

  String open(UUID tenantId, byte[] ciphertext, byte[] wrappedKey, String keyVersion) {
    byte[] dataKey = new byte[0];
    byte[] masterKey = new byte[0];
    try {
      String aad = aad(tenantId);
      masterKey = kms.dataKey(keyVersion);
      dataKey = crypt(Cipher.DECRYPT_MODE, masterKey, wrappedKey, "wrap:" + aad);
      byte[] plaintext = crypt(Cipher.DECRYPT_MODE, dataKey, ciphertext, aad);
      try {
        return new String(plaintext, StandardCharsets.UTF_8);
      } finally {
        Arrays.fill(plaintext, (byte) 0);
      }
    } catch (GeneralSecurityException | IllegalArgumentException error) {
      throw new IllegalStateException("Tenant credential decryption failed", error);
    } finally {
      Arrays.fill(dataKey, (byte) 0);
      Arrays.fill(masterKey, (byte) 0);
    }
  }

  private byte[] crypt(int mode, byte[] key, byte[] input, String aad)
      throws GeneralSecurityException {
    byte[] nonce = new byte[NONCE_BYTES];
    int offset = 0;
    if (mode == Cipher.ENCRYPT_MODE) random.nextBytes(nonce);
    else {
      if (input.length < NONCE_BYTES + TAG_BITS / 8) {
        throw new IllegalArgumentException("Invalid tenant credential");
      }
      System.arraycopy(input, 0, nonce, 0, NONCE_BYTES);
      offset = NONCE_BYTES;
    }
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
    cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
    byte[] result = cipher.doFinal(input, offset, input.length - offset);
    return mode == Cipher.DECRYPT_MODE
        ? result
        : ByteBuffer.allocate(NONCE_BYTES + result.length).put(nonce).put(result).array();
  }

  private static String aad(UUID tenantId) {
    return "tenant-database:" + tenantId;
  }

  record Sealed(byte[] ciphertext, byte[] wrappedKey, String keyVersion) {}
}
