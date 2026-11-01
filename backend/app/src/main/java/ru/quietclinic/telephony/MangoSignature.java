package ru.quietclinic.telephony;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Подпись Mango: SHA-256(vpbx_api_key + json + vpbx_api_salt), без передачи salt в запросе. */
final class MangoSignature {
  private MangoSignature() {}

  static String sign(String key, String json, String salt) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest((key + json + salt).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable", error);
    }
  }

  static boolean valid(String key, String json, String salt, String presented) {
    if (presented == null) return false;
    byte[] expected = sign(key, json, salt).getBytes(StandardCharsets.US_ASCII);
    byte[] actual = presented.trim().getBytes(StandardCharsets.US_ASCII);
    return MessageDigest.isEqual(expected, actual);
  }
}
