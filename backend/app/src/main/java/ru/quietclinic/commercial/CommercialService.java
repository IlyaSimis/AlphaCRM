package ru.quietclinic.commercial;

import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;
import static ru.quietclinic.platform.PlatformConfig.tooManyRequests;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.commercial.CommercialPaymentGateway.PaymentCommand;
import ru.quietclinic.platform.Permission;

/** Тариф клиники, квота внешней модели и hosted checkout. Не связан с кассой пациентов. */
@Service
public class CommercialService {
  private static final List<CommercialPlan> PLANS =
      List.of(
          new CommercialPlan(
              "FREE", "Старт", "Локальный поиск и знакомство с ИИ-навигацией", 0, "RUB", 100),
          new CommercialPlan(
              "TEAM", "Команда", "Для регулярной работы небольшой команды", 199000, "RUB", 3000),
          new CommercialPlan(
              "CLINIC", "Клиника", "Увеличенный общий лимит для клиники", 499000, "RUB", 10000));

  private final JdbcTemplate jdbc;
  private final CommercialPaymentGateway gateway;

  CommercialService(JdbcTemplate jdbc, CommercialPaymentGateway gateway) {
    this.jdbc = jdbc;
    this.gateway = gateway;
  }

  @Transactional(readOnly = true)
  public SubscriptionView subscription() {
    requirePermission(Permission.SUBSCRIPTION_MANAGE);
    var usage = usage();
    var payments =
        jdbc.query(
            "select * from commercial.payment where clinic_id = ? order by created_at desc limit 20",
            (rs, rowNum) ->
                new PaymentView(
                    rs.getObject("id", UUID.class),
                    rs.getString("plan_code"),
                    rs.getLong("amount_minor"),
                    rs.getString("currency"),
                    rs.getString("status"),
                    rs.getString("provider"),
                    rs.getTimestamp("created_at").toInstant(),
                    instant(rs.getTimestamp("paid_at"))),
            clinicId());
    return new SubscriptionView(
        usage.planCode(),
        usage.planName(),
        periodEnd(),
        usage.monthlyAiRequests(),
        usage.usedAiRequests(),
        usage.remainingAiRequests(),
        gateway.enabled(),
        PLANS,
        payments);
  }

  @Transactional(readOnly = true)
  public UsageView usage() {
    var plan = currentPlan();
    var used = requestCount();
    return new UsageView(
        plan.code(),
        plan.name(),
        plan.monthlyAiRequests(),
        used,
        Math.max(0, plan.monthlyAiRequests() - used));
  }

  /** Атомарно резервирует одно обращение. При ошибке провайдера резерв снимается. */
  @Transactional
  public UsageView reserveAiRequest() {
    var limit = currentPlan().monthlyAiRequests();
    lockClinic(clinicId());
    var counts =
        jdbc.query(
            "select request_count from assistant.usage_counter where clinic_id = ? and usage_month = ?",
            (rs, rowNum) -> rs.getInt(1),
            clinicId(),
            month());
    var used = counts.isEmpty() ? 0 : counts.get(0);
    if (used >= limit) {
      throw tooManyRequests("ASSISTANT_LIMIT_REACHED", "Monthly AI request limit reached");
    }
    if (counts.isEmpty()) {
      jdbc.update(
          "insert into assistant.usage_counter(clinic_id, usage_month, request_count, input_tokens,"
              + " output_tokens) values (?, ?, 1, 0, 0)",
          clinicId(),
          month());
    } else {
      jdbc.update(
          "update assistant.usage_counter set request_count = request_count + 1 where clinic_id = ?"
              + " and usage_month = ?",
          clinicId(),
          month());
    }
    return usage();
  }

  @Transactional
  public void releaseAiRequest() {
    jdbc.update(
        "update assistant.usage_counter set request_count = greatest(0, request_count - 1) where"
            + " clinic_id = ? and usage_month = ?",
        clinicId(),
        month());
  }

  @Transactional
  public void recordTokens(long inputTokens, long outputTokens) {
    jdbc.update(
        "update assistant.usage_counter set input_tokens = input_tokens + ?, output_tokens ="
            + " output_tokens + ? where clinic_id = ? and usage_month = ?",
        Math.max(0, inputTokens),
        Math.max(0, outputTokens),
        clinicId(),
        month());
  }

  @Transactional
  public CheckoutView checkout(CheckoutRequest request) {
    requirePermission(Permission.SUBSCRIPTION_MANAGE);
    if (!gateway.enabled()) {
      throw conflict(
          "PAYMENT_PROVIDER_NOT_CONFIGURED", "Subscription payment provider is not configured");
    }
    var plan = plan(request.planCode());
    var id = UUID.randomUUID();
    var now = Instant.now();
    jdbc.update(
        "insert into commercial.payment(id, clinic_id, plan_code, amount_minor, currency, status,"
            + " provider, idempotency_key, created_at, updated_at) values (?, ?, ?, ?, ?, 'PENDING',"
            + " 'YOOKASSA', ?, ?, ?)",
        id,
        clinicId(),
        plan.code(),
        plan.priceMinor(),
        plan.currency(),
        id.toString(),
        Timestamp.from(now),
        Timestamp.from(now));
    try {
      var checkout =
          gateway.createCheckout(
              new PaymentCommand(id, clinicId(), plan.name(), plan.priceMinor(), plan.currency()));
      jdbc.update(
          "update commercial.payment set provider_ref = ?, updated_at = ? where id = ?",
          checkout.providerRef(),
          Timestamp.from(Instant.now()),
          id);
      return new CheckoutView(id, checkout.checkoutUrl());
    } catch (RuntimeException error) {
      jdbc.update(
          "update commercial.payment set status = 'FAILED', updated_at = ? where id = ?",
          Timestamp.from(Instant.now()),
          id);
      throw error;
    }
  }

  /** Вебхук сам по себе не активирует тариф: статус повторно запрошен у провайдера. */
  @Transactional
  public void confirmYooKassa(String providerRef) {
    var remote = gateway.getPayment(providerRef);
    var rows =
        jdbc.query(
            "select * from commercial.payment where provider = 'YOOKASSA' and provider_ref = ?",
            (rs, rowNum) ->
                new StoredPayment(
                    rs.getObject("id", UUID.class),
                    rs.getObject("clinic_id", UUID.class),
                    rs.getString("plan_code"),
                    rs.getLong("amount_minor"),
                    rs.getString("currency"),
                    rs.getString("status")),
            providerRef);
    if (rows.isEmpty())
      throw notFound("SUBSCRIPTION_PAYMENT_NOT_FOUND", "Subscription payment not found");
    var stored = rows.get(0);
    if (stored.status().equals("SUCCEEDED")) return;
    if (!remote.paymentId().equals(stored.id())
        || remote.amountMinor() != stored.amountMinor()
        || !remote.currency().equals(stored.currency())) {
      throw conflict("PAYMENT_VERIFICATION_FAILED", "Payment details do not match");
    }
    var status =
        switch (remote.status()) {
          case "succeeded" -> remote.paid() ? "SUCCEEDED" : "PENDING";
          case "canceled" -> "CANCELED";
          default -> "PENDING";
        };
    var now = Instant.now();
    jdbc.update(
        "update commercial.payment set status = ?, paid_at = ?, updated_at = ? where id = ?",
        status,
        status.equals("SUCCEEDED") ? Timestamp.from(now) : null,
        Timestamp.from(now),
        stored.id());
    if (status.equals("SUCCEEDED")) {
      lockClinic(stored.clinicId());
      var periodEnd = Timestamp.from(now.plusSeconds(30L * 24 * 60 * 60));
      var changed =
          jdbc.update(
              "update commercial.subscription set plan_code = ?, current_period_end = ?, updated_at = ?"
                  + " where clinic_id = ?",
              stored.planCode(),
              periodEnd,
              Timestamp.from(now),
              stored.clinicId());
      if (changed == 0) {
        jdbc.update(
            "insert into commercial.subscription(clinic_id, plan_code, current_period_end, updated_at)"
                + " values (?, ?, ?, ?)",
            stored.clinicId(),
            stored.planCode(),
            periodEnd,
            Timestamp.from(now));
      }
    }
  }

  /** Короткая блокировка сериализует квоту и активацию клиники на PostgreSQL и H2. */
  private void lockClinic(UUID id) {
    jdbc.queryForObject("select id from public.clinic where id = ? for update", UUID.class, id);
  }

  private CommercialPlan currentPlan() {
    var rows =
        jdbc.query(
            "select plan_code, current_period_end from commercial.subscription where clinic_id = ?",
            (rs, rowNum) ->
                new CurrentSubscription(
                    rs.getString("plan_code"), instant(rs.getTimestamp("current_period_end"))),
            clinicId());
    if (rows.isEmpty()
        || rows.get(0).periodEnd() == null
        || !rows.get(0).periodEnd().isAfter(Instant.now())) {
      return plan("FREE");
    }
    return plan(rows.get(0).planCode());
  }

  private Instant periodEnd() {
    var rows =
        jdbc.query(
            "select current_period_end from commercial.subscription where clinic_id = ?",
            (rs, rowNum) -> instant(rs.getTimestamp(1)),
            clinicId());
    return rows.isEmpty() ? null : rows.get(0);
  }

  private int requestCount() {
    var rows =
        jdbc.query(
            "select request_count from assistant.usage_counter where clinic_id = ? and usage_month = ?",
            (rs, rowNum) -> rs.getInt(1),
            clinicId(),
            month());
    return rows.isEmpty() ? 0 : rows.get(0);
  }

  private static CommercialPlan plan(String code) {
    return PLANS.stream()
        .filter(item -> item.code().equals(code))
        .findFirst()
        .orElseThrow(() -> conflict("SUBSCRIPTION_PLAN_NOT_FOUND", "Subscription plan not found"));
  }

  private static String month() {
    return YearMonth.now(ZoneOffset.UTC).toString();
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  public record UsageView(
      String planCode,
      String planName,
      int monthlyAiRequests,
      int usedAiRequests,
      int remainingAiRequests) {}

  public record SubscriptionView(
      String planCode,
      String planName,
      Instant currentPeriodEnd,
      int monthlyAiRequests,
      int usedAiRequests,
      int remainingAiRequests,
      boolean checkoutEnabled,
      List<CommercialPlan> plans,
      List<PaymentView> payments) {}

  public record PaymentView(
      UUID id,
      String planCode,
      long amountMinor,
      String currency,
      String status,
      String provider,
      Instant createdAt,
      Instant paidAt) {}

  public record CheckoutView(UUID paymentId, String checkoutUrl) {}

  private record CurrentSubscription(String planCode, Instant periodEnd) {}

  private record StoredPayment(
      UUID id, UUID clinicId, String planCode, long amountMinor, String currency, String status) {}
}
