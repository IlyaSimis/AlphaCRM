package ru.quietclinic.integrations;

import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.quietclinic.platform.ClinicTime;
import ru.quietclinic.platform.Permission;

/** Пакетная выгрузка фактических записей сайта в Метрику без повторной отправки принятых строк. */
@Service
class MetricaOfflineConversionService {
  private static final int BATCH_SIZE = 500;
  private static final String ELIGIBLE_STATUSES =
      "('BOOKED', 'CONFIRMED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED')";

  private final JdbcTemplate jdbc;
  private final YandexMetricaClientFactory connections;
  private final YandexMetricaGateway metrica;
  private final ClinicTime time;

  MetricaOfflineConversionService(
      JdbcTemplate jdbc,
      YandexMetricaClientFactory connections,
      YandexMetricaGateway metrica,
      ClinicTime time) {
    this.jdbc = jdbc;
    this.connections = connections;
    this.metrica = metrica;
    this.time = time;
  }

  OfflineConversionsView status() {
    requirePermission(Permission.INTEGRATION_READ);
    UUID clinicId = clinicId();
    YandexMetricaClientFactory.Connection connection = connections.forClinic(clinicId).orElse(null);
    boolean configured =
        connection != null
            && "ACTIVE".equals(connection.status())
            && !connection.offlineGoalId().isBlank()
            && !connection.oauthToken().isBlank();
    long attributed = countAttributed(clinicId, false);
    long pending = countAttributed(clinicId, true);
    ConversionCounts counts =
        jdbc.queryForObject(
            "select coalesce(sum(case when status = 'UPLOADING' then 1 else 0 end), 0) as uploading,"
                + " coalesce(sum(case when status = 'UPLOADED' then 1 else 0 end), 0) as uploaded,"
                + " coalesce(sum(case when status = 'FAILED' then 1 else 0 end), 0) as failed,"
                + " max(uploaded_at) as last_uploaded_at from metrica_offline_conversion"
                + " where clinic_id = ?",
            (row, index) ->
                new ConversionCounts(
                    row.getLong("uploading"),
                    row.getLong("uploaded"),
                    row.getLong("failed"),
                    instant(row.getTimestamp("last_uploaded_at"))),
            clinicId);
    List<String> errors =
        jdbc.query(
            "select last_error from metrica_offline_conversion where clinic_id = ?"
                + " and last_error is not null order by updated_at desc limit 1",
            (row, index) -> row.getString("last_error"),
            clinicId);
    return new OfflineConversionsView(
        configured,
        attributed,
        pending,
        counts.uploading,
        counts.uploaded,
        counts.failed,
        counts.lastUploadedAt,
        errors.isEmpty() ? null : errors.get(0));
  }

  /**
   * Один процесс не отправляет параллельные пакеты; уникальный appointment_id исключает дубли в
   * журнале.
   */
  synchronized OfflineConversionUploadView upload() {
    requirePermission(Permission.INTEGRATION_MANAGE);
    UUID clinicId = clinicId();
    YandexMetricaClientFactory.Connection connection = requireReady(clinicId);
    Instant now = time.now();
    queueEligible(clinicId, connection.offlineGoalId(), now);
    List<Conversion> batch = due(clinicId);
    if (batch.isEmpty()) return new OfflineConversionUploadView(0, null);
    mark(batch, clinicId, "UPLOADING", now, null, null, null);
    try {
      String uploadingId = metrica.uploadOfflineConversions(connection, csv(batch));
      mark(batch, clinicId, "UPLOADED", now, uploadingId, now, null);
      return new OfflineConversionUploadView(batch.size(), uploadingId);
    } catch (YandexMetricaGateway.ProviderException ex) {
      mark(batch, clinicId, "FAILED", now, null, null, ex.getMessage());
      throw conflict("YANDEX_METRICA_UPLOAD_FAILED", "Yandex Metrica rejected offline conversions");
    } catch (RuntimeException ex) {
      mark(batch, clinicId, "FAILED", now, null, null, "YANDEX_METRICA_UNAVAILABLE");
      throw conflict("YANDEX_METRICA_UNAVAILABLE", "Yandex Metrica is unavailable");
    }
  }

  private YandexMetricaClientFactory.Connection requireReady(UUID clinicId) {
    YandexMetricaClientFactory.Connection connection =
        connections
            .forClinic(clinicId)
            .orElseThrow(() -> notFound("INTEGRATION_NOT_FOUND", "Integration is not configured"));
    if (!"ACTIVE".equals(connection.status())) {
      throw conflict("YANDEX_METRICA_DISABLED", "Yandex Metrica integration is disabled");
    }
    if (connection.counterId().isBlank() || connection.oauthToken().isBlank()) {
      throw conflict(
          "YANDEX_METRICA_CONFIGURATION_REQUIRED", "Counter ID and OAuth token are required");
    }
    if (connection.offlineGoalId().isBlank()) {
      throw conflict("YANDEX_METRICA_GOAL_REQUIRED", "Offline goal ID is required");
    }
    return connection;
  }

  private void queueEligible(UUID clinicId, String target, Instant now) {
    jdbc.update(
        "update metrica_offline_conversion set target = ?, updated_at = ? where clinic_id = ?"
            + " and status in ('PENDING', 'FAILED')",
        target,
        Timestamp.from(now),
        clinicId);
    jdbc.update(
        "insert into metrica_offline_conversion(appointment_id, clinic_id, target, status,"
            + " conversion_at, price_minor, currency, attempts, created_at, updated_at)"
            + " select a.id, p.clinic_id, ?, 'PENDING', a.created_at, a.amount_minor, a.currency,"
            + " 0, ?, ? from appointment a join practitioner p on p.id = a.practitioner_id"
            + " where p.clinic_id = ? and a.metrica_client_id is not null and a.status in "
            + ELIGIBLE_STATUSES
            + " and not exists (select 1 from metrica_offline_conversion c"
            + " where c.appointment_id = a.id)",
        target,
        Timestamp.from(now),
        Timestamp.from(now),
        clinicId);
  }

  private List<Conversion> due(UUID clinicId) {
    return jdbc.query(
        "select c.appointment_id, a.metrica_client_id, c.target, c.conversion_at, c.price_minor,"
            + " c.currency from metrica_offline_conversion c join appointment a"
            + " on a.id = c.appointment_id where c.clinic_id = ?"
            + " and c.status in ('PENDING', 'FAILED') order by c.created_at limit "
            + BATCH_SIZE,
        (row, index) ->
            new Conversion(
                row.getObject("appointment_id", UUID.class),
                row.getString("metrica_client_id"),
                row.getString("target"),
                row.getTimestamp("conversion_at").toInstant(),
                (Long) row.getObject("price_minor"),
                row.getString("currency")),
        clinicId);
  }

  private void mark(
      List<Conversion> batch,
      UUID clinicId,
      String status,
      Instant updatedAt,
      String uploadingId,
      Instant uploadedAt,
      String error) {
    jdbc.batchUpdate(
        "update metrica_offline_conversion set status = ?,"
            + " attempts = attempts + case when ? = 'UPLOADING' then 1 else 0 end,"
            + " uploading_id = coalesce(?, uploading_id), uploaded_at = coalesce(?, uploaded_at),"
            + " updated_at = ?, last_error = ? where appointment_id = ? and clinic_id = ?",
        batch.stream()
            .map(
                conversion ->
                    new Object[] {
                      status,
                      status,
                      uploadingId,
                      uploadedAt == null ? null : Timestamp.from(uploadedAt),
                      Timestamp.from(updatedAt),
                      error,
                      conversion.appointmentId,
                      clinicId
                    })
            .toList());
  }

  private long countAttributed(UUID clinicId, boolean pendingOnly) {
    String pending =
        pendingOnly
            ? " and a.status in "
                + ELIGIBLE_STATUSES
                + " and (c.appointment_id is null or c.status in ('PENDING', 'FAILED'))"
            : "";
    return jdbc.queryForObject(
        "select count(*) from appointment a join practitioner p on p.id = a.practitioner_id"
            + " left join metrica_offline_conversion c on c.appointment_id = a.id"
            + " where p.clinic_id = ? and a.metrica_client_id is not null"
            + pending,
        Long.class,
        clinicId);
  }

  private static String csv(List<Conversion> batch) {
    StringBuilder csv = new StringBuilder("ClientId,Target,DateTime,Price,Currency\r\n");
    for (Conversion conversion : batch) {
      csv.append(conversion.clientId)
          .append(',')
          .append(conversion.target)
          .append(',')
          .append(conversion.conversionAt.getEpochSecond())
          .append(',')
          .append(
              conversion.priceMinor == null
                  ? ""
                  : BigDecimal.valueOf(conversion.priceMinor, 2).toPlainString())
          .append(',')
          .append(conversion.currency == null ? "" : conversion.currency)
          .append("\r\n");
    }
    return csv.toString();
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private record Conversion(
      UUID appointmentId,
      String clientId,
      String target,
      Instant conversionAt,
      Long priceMinor,
      String currency) {}

  private record ConversionCounts(
      long uploading, long uploaded, long failed, Instant lastUploadedAt) {}

  record OfflineConversionsView(
      boolean configured,
      long attributed,
      long pending,
      long uploading,
      long uploaded,
      long failed,
      Instant lastUploadedAt,
      String lastError) {}

  record OfflineConversionUploadView(int uploaded, String uploadingId) {}
}
