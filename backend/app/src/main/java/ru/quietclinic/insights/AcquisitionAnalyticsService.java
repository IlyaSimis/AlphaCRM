package ru.quietclinic.insights;

import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.platform.ClinicTime;
import ru.quietclinic.platform.Permission;

/** Агрегирует источники пациентов; внешний сервис не участвует в расчёте CRM-метрик. */
@Service
class AcquisitionAnalyticsService {
  private final JdbcTemplate jdbc;
  private final ClinicTime time;
  private final WebAnalyticsPort webAnalytics;

  AcquisitionAnalyticsService(JdbcTemplate jdbc, ClinicTime time, WebAnalyticsPort webAnalytics) {
    this.jdbc = jdbc;
    this.time = time;
    this.webAnalytics = webAnalytics;
  }

  @Transactional(readOnly = true)
  AcquisitionAnalyticsView report(LocalDate from, LocalDate to) {
    requirePermission(Permission.ANALYTICS_READ);
    List<AcquisitionAnalyticsView.ReferralSource> sources = referralSources(from, to);
    return new AcquisitionAnalyticsView(
        from, to, "RUB", sources, webAnalytics.load(clinicId(), from, to));
  }

  private List<AcquisitionAnalyticsView.ReferralSource> referralSources(
      LocalDate from, LocalDate to) {
    return jdbc.query(
        "select coalesce(p.referral_source, 'UNSPECIFIED') as source, count(*) as contacts,"
            + " coalesce(sum(case when exists (select 1 from appointment a"
            + " where a.patient_id = p.id) then 1 else 0 end), 0) as booked,"
            + " coalesce(sum(case when exists (select 1 from appointment a"
            + " where a.patient_id = p.id and a.status = 'COMPLETED') then 1 else 0 end), 0) as visited,"
            + " coalesce(sum(case when exists (select 1 from charge c join appointment a"
            + " on a.id = c.appointment_id where a.patient_id = p.id"
            + " and c.status in ('PAID', 'REFUNDED')) then 1 else 0 end), 0) as paid,"
            + " coalesce(sum((select coalesce(sum(c.amount_minor), 0) from payment_record r"
            + " join charge c on c.id = r.charge_id join appointment a on a.id = c.appointment_id"
            + " where a.patient_id = p.id"
            + " and r.payment_status in ('SUCCEEDED', 'MOCK_NO_PROVIDER', 'CONFIRMED'))"
            + " - (select coalesce(sum(c.amount_minor), 0) from refund_record f"
            + " join charge c on c.id = f.charge_id join appointment a on a.id = c.appointment_id"
            + " where a.patient_id = p.id)), 0) as revenue"
            + " from patient p where p.clinic_id = ? and p.created_at >= ? and p.created_at < ?"
            + " group by coalesce(p.referral_source, 'UNSPECIFIED') order by contacts desc, source",
        (row, index) ->
            new AcquisitionAnalyticsView.ReferralSource(
                row.getString("source"),
                row.getLong("contacts"),
                row.getLong("booked"),
                row.getLong("visited"),
                row.getLong("paid"),
                row.getLong("revenue")),
        clinicId(),
        Timestamp.from(time.startOf(from)),
        Timestamp.from(time.startOf(to.plusDays(1))));
  }
}
