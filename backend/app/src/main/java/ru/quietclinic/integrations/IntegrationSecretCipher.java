package ru.quietclinic.integrations;

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

/** Envelope encryption: случайный DEK на версию секрета, DEK завёрнут ключом из KMS. */
@Component
class IntegrationSecretCipher {
  private static final int NONCE_BYTES = 12;
  private static final int GCM_TAG_BITS = 128;

  private final KmsPort kms;
  private final SecureRandom random = new SecureRandom();

  IntegrationSecretCipher(KmsPort kms) {
    this.kms = kms;
  }

  Sealed encrypt(UUID clinicId, UUID integrationId, int version, String plaintext) {
    var dataKey = new byte[32];
    var masterKey = new byte[0];
    var plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
    try {
      random.nextBytes(dataKey);
      var aad = aad(clinicId, integrationId, version);
      masterKey = kms.dataKey();
      return new Sealed(
          crypt(Cipher.ENCRYPT_MODE, dataKey, plaintextBytes, aad),
          crypt(Cipher.ENCRYPT_MODE, masterKey, dataKey, "wrap:" + aad),
          kms.keyVersion());
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Integration secret encryption failed", ex);
    } finally {
      Arrays.fill(dataKey, (byte) 0);
      Arrays.fill(masterKey, (byte) 0);
      Arrays.fill(plaintextBytes, (byte) 0);
    }
  }

  String decrypt(UUID clinicId, UUID integrationId, int version, IntegrationSecretRecord secret) {
    var dataKey = new byte[0];
    var masterKey = new byte[0];
    try {
      var aad = aad(clinicId, integrationId, version);
      masterKey = kms.dataKey(secret.keyVersion());
      dataKey = crypt(Cipher.DECRYPT_MODE, masterKey, secret.wrappedDataKey(), "wrap:" + aad);
      var plaintext = crypt(Cipher.DECRYPT_MODE, dataKey, secret.ciphertext(), aad);
      try {
        return new String(plaintext, StandardCharsets.UTF_8);
      } finally {
        Arrays.fill(plaintext, (byte) 0);
      }
    } catch (GeneralSecurityException | IllegalArgumentException ex) {
      throw new IllegalStateException("Integration secret decryption failed", ex);
    } finally {
      Arrays.fill(dataKey, (byte) 0);
      Arrays.fill(masterKey, (byte) 0);
    }
  }

  private byte[] crypt(int mode, byte[] key, byte[] input, String aad)
      throws GeneralSecurityException {
    var nonce = new byte[NONCE_BYTES];
    var outputOffset = 0;
    if (mode == Cipher.ENCRYPT_MODE) {
      random.nextBytes(nonce);
    } else {
      if (input.length < NONCE_BYTES + GCM_TAG_BITS / 8) {
        throw new IllegalArgumentException("Invalid encrypted payload");
      }
      System.arraycopy(input, 0, nonce, 0, NONCE_BYTES);
      outputOffset = NONCE_BYTES;
    }
    var cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
    cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
    var result = cipher.doFinal(input, outputOffset, input.length - outputOffset);
    if (mode == Cipher.DECRYPT_MODE) return result;
    return ByteBuffer.allocate(NONCE_BYTES + result.length).put(nonce).put(result).array();
  }

  private static String aad(UUID clinicId, UUID integrationId, int version) {
    return clinicId + ":" + integrationId + ":" + version;
  }

  record Sealed(byte[] ciphertext, byte[] wrappedDataKey, String keyVersion) {}
}
