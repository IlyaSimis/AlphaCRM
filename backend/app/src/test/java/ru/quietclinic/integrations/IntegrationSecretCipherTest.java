package ru.quietclinic.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.quietclinic.platform.KmsPort;

class IntegrationSecretCipherTest {
  private final KmsPort kms =
      new KmsPort() {
        @Override
        public byte[] dataKey() {
          return dataKey("test-v1");
        }

        @Override
        public byte[] dataKey(String version) {
          if (!"test-v1".equals(version)) throw new IllegalArgumentException("Unknown key");
          return "01234567890123456789012345678901".getBytes();
        }

        @Override
        public byte[] indexKey() {
          return dataKey();
        }

        @Override
        public String keyVersion() {
          return "test-v1";
        }
      };
  private final IntegrationSecretCipher cipher = new IntegrationSecretCipher(kms);

  @Test
  void roundTripsSecretWithoutStoringPlaintext() {
    var clinic = UUID.randomUUID();
    var integration = UUID.randomUUID();
    var sealed = cipher.encrypt(clinic, integration, 1, "{\"apiKey\":\"secret\"}");
    assertThat(new String(sealed.ciphertext())).doesNotContain("secret");
    var decrypted =
        cipher.decrypt(
            clinic,
            integration,
            1,
            new IntegrationSecretRecord(
                sealed.ciphertext(), sealed.wrappedDataKey(), sealed.keyVersion()));
    assertThat(decrypted).isEqualTo("{\"apiKey\":\"secret\"}");
  }

  @Test
  void rejectsCiphertextMovedToAnotherIntegration() {
    var clinic = UUID.randomUUID();
    var sealed = cipher.encrypt(clinic, UUID.randomUUID(), 1, "secret");
    var record =
        new IntegrationSecretRecord(
            sealed.ciphertext(), sealed.wrappedDataKey(), sealed.keyVersion());
    assertThatThrownBy(() -> cipher.decrypt(clinic, UUID.randomUUID(), 1, record))
        .isInstanceOf(IllegalStateException.class);
  }
}
