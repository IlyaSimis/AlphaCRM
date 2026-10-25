package ru.quietclinic.communications;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Подпись тела запроса HMAC-SHA256: получатель проверяет, что запрос пришёл от этой CRM и не
 * изменён. Подписывается строка «timestamp.тело», чтобы старый запрос нельзя было повторить. Тем же
 * способом подписываются запросы в онлайн-кассу.
 */
public final class Signature {
  public static final String SIGNATURE_HEADER = "X-AlphaCRM-Signature";
  public static final String TIMESTAMP_HEADER = "X-AlphaCRM-Timestamp";
  public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

  private Signature() {}

  /** {@code sha256=<hex>} от HMAC-SHA256(secret, timestamp + "." + body). */
  public static String sign(String secret, long timestamp, String body) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      var digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
      var hex = new StringBuilder("sha256=");
      for (var b : digest) hex.append(String.format("%02x", b));
      return hex.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("Signing failed", ex);
    }
  }
}
