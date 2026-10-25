package ru.quietclinic.billing;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ru.quietclinic.communications.FiscalPort;
import ru.quietclinic.communications.FiscalResult;
import ru.quietclinic.communications.Signature;

/**
 * Порт онлайн-кассы (CLINIC_CASHBOX_URL). Пока адрес не задан, чек не отправляется и платёж
 * остаётся с fiscalStatus QUEUED. В кассу уходят сумма, валюта, id платежа и счёта — без ФИО,
 * контактов и данных приёма.
 */
@Component
class CashboxFiscalAdapter implements FiscalPort {
  static final String SENT_STATUS = "SENT_TO_CASHBOX";

  private final PaymentRepository payments;
  private final ChargeRepository charges;
  private final TransactionTemplate tx;
  private final String url;
  private final String secret;
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  CashboxFiscalAdapter(
      PaymentRepository payments,
      ChargeRepository charges,
      TransactionTemplate tx,
      @Value("${clinic.cashbox.url:}") String url,
      @Value("${clinic.cashbox.secret:}") String secret) {
    this.payments = payments;
    this.charges = charges;
    this.tx = tx;
    this.url = url == null ? "" : url.trim();
    this.secret = secret == null ? "" : secret;
  }

  @Override
  public FiscalResult sendReceipt(UUID paymentId, String idempotencyKey) {
    if (url.isEmpty() || secret.isBlank()) return FiscalResult.notConfigured();
    var body = tx.execute(status -> body(paymentId, idempotencyKey));
    if (body == null) return new FiscalResult("REJECTED", "PAYMENT_NOT_FOUND");
    var timestamp = Instant.now().getEpochSecond();
    try {
      var request =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(10))
              .header("Content-Type", "application/json; charset=utf-8")
              .header(Signature.IDEMPOTENCY_HEADER, idempotencyKey)
              .header(Signature.TIMESTAMP_HEADER, Long.toString(timestamp))
              .header(Signature.SIGNATURE_HEADER, Signature.sign(secret, timestamp, body))
              .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
              .build();
      var status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
      var result = FiscalResult.ofHttp(status);
      if ("SENT".equals(result.outcome())) {
        tx.executeWithoutResult(
            s -> payments.findById(paymentId).ifPresent(p -> p.fiscalStatus = SENT_STATUS));
      }
      return result;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return FiscalResult.retry("NETWORK_ERROR");
    } catch (IOException | IllegalArgumentException ex) {
      return FiscalResult.retry("NETWORK_ERROR");
    }
  }

  private String body(UUID paymentId, String idempotencyKey) {
    var payment = payments.findById(paymentId).orElse(null);
    if (payment == null) return null;
    var charge = charges.findById(payment.chargeId).orElse(null);
    if (charge == null) return null;
    return "{\"idempotencyKey\":\""
        + idempotencyKey
        + "\",\"operation\":\"receipt\",\"paymentId\":\""
        + payment.id
        + "\",\"chargeId\":\""
        + charge.id
        + "\",\"amountMinor\":"
        + charge.amountMinor
        + ",\"currency\":\""
        + charge.currency
        + "\",\"paidAt\":\""
        + payment.createdAt
        + "\"}";
  }
}
