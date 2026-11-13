package ru.quietclinic.billing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 сырого JSON. Адаптер поставщика подписывает уведомление этим секретом. */
final class TerminalSignature {
  private TerminalSignature() {}

  static boolean valid(String secret, byte[] body, String signature) {
    if (signature == null || !signature.matches("[0-9a-fA-F]{64}")) return false;
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return MessageDigest.isEqual(mac.doFinal(body), hex(signature));
    } catch (Exception error) {
      throw new IllegalStateException("HmacSHA256 is unavailable", error);
    }
  }

  private static byte[] hex(String value) {
    byte[] result = new byte[value.length() / 2];
    for (int i = 0; i < result.length; i++) {
      result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
    }
    return result;
  }
}
