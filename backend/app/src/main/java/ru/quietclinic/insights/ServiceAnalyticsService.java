package ru.quietclinic.insights;

import static ru.quietclinic.platform.PlatformConfig.badRequest;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.platform.ClinicTime;
import ru.quietclinic.platform.Permission;

/** Услуги: посещаемость по дате приёма, выручка по дате оплаты или возврата. */
@Service
class ServiceAnalyticsService {
  private static final int MAX_RANGE_DAYS = 366;
  private static final String LEGACY_KEY_PREFIX = "name:";

  private final JdbcTemplate jdbc;
  private final ClinicTime time;

  ServiceAnalyticsService(JdbcTemplate jdbc, ClinicTime time) {
    this.jdbc = jdbc;
    this.time = time;
  }

  @Transactional(readOnly = true)
  ServiceAnalyticsView report(LocalDate from, LocalDate to, UUID practitionerId) {
    requirePermission(Permission.ANALYTICS_READ);
    validateRange(from, to);
    long days = ChronoUnit.DAYS.between(from, to) + 1;
    LocalDate previousTo = from.minusDays(1);
    LocalDate previousFrom = previousTo.minusDays(days - 1);
    PeriodData current = period(from, to, practitionerId);
    PeriodData previous = period(previousFrom, previousTo, practitionerId);

    Map<String, String> names = new LinkedHashMap<>(previous.names());
    names.putAll(current.names());
    List<ServiceAnalyticsView.Row> rows = new ArrayList<>();
    for (Map.Entry<String, String> entry : names.entrySet()) {
      String key = entry.getKey();
      Accumulator row = current.rows().getOrDefault(key, new Accumulator());
      Accumulator old = previous.rows().getOrDefault(key, new Accumulator());
      rows.add(row(entry.getValue(), key, row, old));
    }
    rows.sort(
        Comparator.comparingLong(ServiceAnalyticsView.Row::revenueMinor)
            .reversed()
            .thenComparing(ServiceAnalyticsView.Row::serviceName));
    return new ServiceAnalyticsView(
        from,
        to,
        previousFrom,
        previousTo,
        "RUB",
        totals(current.total()),
        totals(previous.total()),
        List.copyOf(rows));
  }

  @Transactional(readOnly = true)
  ServiceAnalyticsView.Details details(
      LocalDate from, LocalDate to, String serviceKey, UUID practitionerId) {
    requirePermission(Permission.ANALYTICS_READ);
    requirePermission(Permission.PATIENT_READ);
    validateRange(from, to);
    ServiceFilter service = serviceFilter(serviceKey);
    String serviceName = requiredServiceName(service);
    String sql =
        "select a.id, a.patient_id, patient.full_name as patient_name, a.practitioner_id,"
            + " practitioner.full_name as practitioner_name, a.starts_at, a.status,"
            + " c.id as charge_id, c.status as charge_status, c.amount_minor,"
            + " coalesce(c.currency, a.currency) as currency,"
            + " case when not exists (select 1 from appointment later"
            + " where later.patient_id = a.patient_id and later.starts_at > a.starts_at"
            + " and later.status <> 'CANCELLED') then true else false end as without_next"
            + " from appointment a"
            + " join patient on patient.id = a.patient_id"
            + " join practitioner on practitioner.id = a.practitioner_id"
            + " left join charge c on c.appointment_id = a.id"
            + " where practitioner.clinic_id = ? and a.starts_at >= ? and a.starts_at < ?"
            + service.condition()
            + (practitionerId == null ? "" : " and a.practitioner_id = ?")
            + " order by a.starts_at desc, patient.full_name";
    List<Object> args = new ArrayList<>();
    args.add(clinicId());
    args.add(Timestamp.from(time.startOf(from)));
    args.add(Timestamp.from(time.startOf(to.plusDays(1))));
    args.addAll(service.args());
    if (practitionerId != null) args.add(practitionerId);
    List<ServiceAnalyticsView.Appointment> appointments =
        jdbc.query(
            sql,
            (rs, rowNumber) ->
                new ServiceAnalyticsView.Appointment(
                    UUID.fromString(rs.getString("id")),
                    UUID.fromString(rs.getString("patient_id")),
                    rs.getString("patient_name"),
                    UUID.fromString(rs.getString("practitioner_id")),
                    rs.getString("practitioner_name"),
                    rs.getTimestamp("starts_at").toInstant(),
                    rs.getString("status"),
                    rs.getString("charge_id") == null
                        ? null
                        : UUID.fromString(rs.getString("charge_id")),
                    rs.getString("charge_status"),
                    rs.getObject("amount_minor") == null ? null : rs.getLong("amount_minor"),
                    rs.getString("currency"),
                    rs.getBoolean("without_next")),
            args.toArray());
    return new ServiceAnalyticsView.Details(from, to, serviceKey, serviceName, appointments);
  }

  private String requiredServiceName(ServiceFilter service) {
    List<Object> args = new ArrayList<>();
    args.add(clinicId());
    args.addAll(service.args());
    return jdbc
        .query(
            "select a.service_name from appointment a join practitioner p on p.id = a.practitioner_id"
                + " where p.clinic_id = ?"
                + service.condition()
                + " order by a.starts_at desc limit 1",
            (rs, rowNumber) -> rs.getString(1),
            args.toArray())
        .stream()
        .findFirst()
        .orElseThrow(() -> notFound("SERVICE_NOT_FOUND", "Service not found"));
  }

  private PeriodData period(LocalDate from, LocalDate to, UUID practitionerId) {
    Timestamp start = Timestamp.from(time.startOf(from));
    Timestamp end = Timestamp.from(time.startOf(to.plusDays(1)));
    Map<String, Accumulator> rows = new HashMap<>();
    Map<String, String> names = new LinkedHashMap<>();
    Accumulator total = new Accumulator();
    for (AppointmentMetric appointment : appointmentMetrics(start, end, practitionerId)) {
      String key = key(appointment.serviceId(), appointment.serviceName());
      names.put(key, appointment.serviceName());
      rows.computeIfAbsent(key, ignored -> new Accumulator()).add(appointment);
      total.add(appointment);
    }
    for (FinanceMetric finance : financeMetrics(start, end, practitionerId, false)) {
      String key = key(finance.serviceId(), finance.serviceName());
      names.putIfAbsent(key, finance.serviceName());
      rows.computeIfAbsent(key, ignored -> new Accumulator()).add(finance);
      total.add(finance);
    }
    for (FinanceMetric finance : financeMetrics(start, end, practitionerId, true)) {
      String key = key(finance.serviceId(), finance.serviceName());
      names.putIfAbsent(key, finance.serviceName());
      rows.computeIfAbsent(key, ignored -> new Accumulator()).add(finance);
      total.add(finance);
    }
    return new PeriodData(rows, names, total);
  }

  private List<AppointmentMetric> appointmentMetrics(
      Timestamp start, Timestamp end, UUID practitionerId) {
    String filter = practitionerId == null ? "" : " and a.practitioner_id = ?";
    return jdbc.query(
        "select a.patient_id, a.service_id, a.service_name, a.status,"
            + " case when a.status = 'COMPLETED' and not exists (select 1 from appointment earlier"
            + " where earlier.patient_id = a.patient_id and earlier.status = 'COMPLETED'"
            + " and earlier.starts_at < a.starts_at) then true else false end as first_completed,"
            + " case when a.status = 'COMPLETED' and not exists (select 1 from appointment later"
            + " where later.patient_id = a.patient_id and later.starts_at > a.starts_at"
            + " and later.status <> 'CANCELLED') then true else false end as without_next"
            + " from appointment a join practitioner p on p.id = a.practitioner_id"
            + " where p.clinic_id = ? and a.starts_at >= ? and a.starts_at < ?" + filter,
        (rs, rowNumber) ->
            new AppointmentMetric(
                UUID.fromString(rs.getString("patient_id")),
                rs.getString("service_id") == null ? null : UUID.fromString(rs.getString("service_id")),
                rs.getString("service_name"),
                rs.getString("status"),
                rs.getBoolean("first_completed"),
                rs.getBoolean("without_next")),
        args(practitionerId, clinicId(), start, end));
  }

  private List<FinanceMetric> financeMetrics(
      Timestamp start, Timestamp end, UUID practitionerId, boolean refund) {
    String filter = practitionerId == null ? "" : " and a.practitioner_id = ?";
    String sql =
        refund
            ? "select a.service_id, a.service_name, c.amount_minor"
                + " from refund_record f join charge c on c.id = f.charge_id"
                + " join appointment a on a.id = c.appointment_id"
                + " join practitioner p on p.id = a.practitioner_id"
                + " where p.clinic_id = ? and f.created_at >= ? and f.created_at < ?"
            : "select a.service_id, a.service_name, c.amount_minor"
                + " from payment_record r join charge c on c.id = r.charge_id"
                + " join appointment a on a.id = c.appointment_id"
                + " join practitioner p on p.id = a.practitioner_id"
                + " where p.clinic_id = ? and coalesce(r.completed_at, r.created_at) >= ?"
                + " and coalesce(r.completed_at, r.created_at) < ?"
                + " and r.payment_status in ('SUCCEEDED', 'MOCK_NO_PROVIDER', 'CONFIRMED')";
    return jdbc.query(
        sql + filter,
        (rs, rowNumber) ->
            new FinanceMetric(
                rs.getString("service_id") == null ? null : UUID.fromString(rs.getString("service_id")),
                rs.getString("service_name"),
                rs.getLong("amount_minor"),
                refund),
        args(practitionerId, clinicId(), start, end));
  }

  private ServiceAnalyticsView.Row row(
      String name, String key, Accumulator current, Accumulator previous) {
    return new ServiceAnalyticsView.Row(
        key,
        name,
        current.appointments,
        current.visits,
        current.patients.size(),
        current.newPatients.size(),
        current.patients.size() - current.newPatients.size(),
        current.noShow,
        current.cancelled,
        current.withoutNext.size(),
        current.revenue(),
        current.payments,
        current.averageCheck(),
        previous.visits,
        previous.revenue());
  }

  private static ServiceAnalyticsView.Totals totals(Accumulator value) {
    return new ServiceAnalyticsView.Totals(
        value.appointments,
        value.visits,
        value.patients.size(),
        value.newPatients.size(),
        value.patients.size() - value.newPatients.size(),
        value.noShow,
        value.cancelled,
        value.withoutNext.size(),
        value.revenue(),
        value.payments,
        value.averageCheck());
  }

  private ServiceFilter serviceFilter(String serviceKey) {
    if (serviceKey == null || serviceKey.isBlank()) {
      throw badRequest("SERVICE_KEY_REQUIRED", "Service key is required");
    }
    if (serviceKey.startsWith(LEGACY_KEY_PREFIX)) {
      String name = serviceKey.substring(LEGACY_KEY_PREFIX.length());
      if (name.isBlank()) throw badRequest("INVALID_SERVICE_KEY", "Service key is invalid");
      return new ServiceFilter(" and a.service_id is null and a.service_name = ?", List.of(name));
    }
    try {
      return new ServiceFilter(" and a.service_id = ?", List.of(UUID.fromString(serviceKey)));
    } catch (IllegalArgumentException exception) {
      throw badRequest("INVALID_SERVICE_KEY", "Service key is invalid");
    }
  }

  private static String key(UUID serviceId, String serviceName) {
    return serviceId == null ? LEGACY_KEY_PREFIX + serviceName : serviceId.toString();
  }

  private void validateRange(LocalDate from, LocalDate to) {
    if (from == null || to == null || to.isBefore(from)) {
      throw conflict("INVALID_RANGE", "Period end must not be before its start");
    }
    if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
      throw conflict("RANGE_TOO_LONG", "Period must not exceed one year");
    }
  }

  private static Object[] args(UUID practitionerId, Object... head) {
    if (practitionerId == null) return head;
    Object[] result = new Object[head.length + 1];
    System.arraycopy(head, 0, result, 0, head.length);
    result[head.length] = practitionerId;
    return result;
  }

  private record AppointmentMetric(
      UUID patientId,
      UUID serviceId,
      String serviceName,
      String status,
      boolean firstCompleted,
      boolean withoutNext) {}

  private record FinanceMetric(UUID serviceId, String serviceName, long amount, boolean refund) {}

  private record PeriodData(
      Map<String, Accumulator> rows, Map<String, String> names, Accumulator total) {}

  private record ServiceFilter(String condition, List<Object> args) {}

  private static final class Accumulator {
    long appointments;
    long visits;
    long noShow;
    long cancelled;
    long paid;
    long refunded;
    long payments;
    final Set<UUID> patients = new HashSet<>();
    final Set<UUID> newPatients = new HashSet<>();
    final Set<UUID> withoutNext = new HashSet<>();

    void add(AppointmentMetric appointment) {
      appointments++;
      if ("COMPLETED".equals(appointment.status())) {
        visits++;
        patients.add(appointment.patientId());
        if (appointment.firstCompleted()) newPatients.add(appointment.patientId());
        if (appointment.withoutNext()) withoutNext.add(appointment.patientId());
      }
      if ("NO_SHOW".equals(appointment.status())) noShow++;
      if ("CANCELLED".equals(appointment.status())) cancelled++;
    }

    void add(FinanceMetric finance) {
      if (finance.refund()) refunded += finance.amount();
      else {
        paid += finance.amount();
        payments++;
      }
    }

    long revenue() {
      return paid - refunded;
    }

    Long averageCheck() {
      return payments == 0 ? null : Math.round((double) paid / payments);
    }
  }
}
