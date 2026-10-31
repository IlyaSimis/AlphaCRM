package ru.quietclinic.insights;

import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.insights.AnalyticsOverviewView.Funnel;
import ru.quietclinic.insights.AnalyticsOverviewView.Load;
import ru.quietclinic.insights.AnalyticsOverviewView.NextSlot;
import ru.quietclinic.insights.AnalyticsOverviewView.Queues;
import ru.quietclinic.insights.AnalyticsOverviewView.Totals;
import ru.quietclinic.insights.AnalyticsOverviewView.Week;
import ru.quietclinic.platform.ClinicTime;
import ru.quietclinic.platform.Permission;

/** Расчёт управленческих метрик на сервере: в интерфейс уходят только агрегаты. */
@Service
class AnalyticsOverviewService {
  private static final int MAX_RANGE_DAYS = 366;
  private static final int NEXT_SLOT_DAYS = 30;
  private static final int NEXT_SLOT_MINUTES = 30;

  private final JdbcTemplate jdbc;
  private final ClinicTime time;

  AnalyticsOverviewService(JdbcTemplate jdbc, ClinicTime time) {
    this.jdbc = jdbc;
    this.time = time;
  }

  @Transactional(readOnly = true)
  AnalyticsOverviewView overview(LocalDate from, LocalDate to, UUID practitionerId) {
    requirePermission(Permission.ANALYTICS_READ);
    validateRange(from, to);

    var practitioners = practitioners(practitionerId);
    var shifts = shifts(practitionerId);
    var appointments = appointments(from, to, practitionerId);
    var finance = finance(from, to, practitionerId);
    var capacity = capacity(from, to, practitioners, shifts, appointments);
    var weeks = weekly(from, to, appointments, finance, capacity.weeks());

    long attended = appointments.stream().filter(a -> attended(a.status())).count();
    long noShow = appointments.stream().filter(a -> "NO_SHOW".equals(a.status())).count();
    long cancelled = appointments.stream().filter(a -> "CANCELLED".equals(a.status())).count();
    long payments = finance.stream().filter(f -> !f.refund()).count();
    long paid = finance.stream().filter(f -> !f.refund()).mapToLong(FinanceRow::amount).sum();
    long refunded = finance.stream().filter(FinanceRow::refund).mapToLong(FinanceRow::amount).sum();
    long open = finance.stream().filter(f -> !f.refund() && "OPEN".equals(f.priceMode())).mapToLong(FinanceRow::amount).sum();
    var debt = debt(practitionerId);
    var patients = patientMetrics(from, to, practitionerId);
    var retention = retention(from, to, practitionerId);
    var waitlist = waitlist(from, to, practitionerId);
    long occupied = capacity.practitioners().stream().mapToLong(Load::occupiedMinutes).sum();
    long available = capacity.practitioners().stream().mapToLong(Load::availableMinutes).sum();
    var totals = new Totals(
        appointments.size(), attended, noShow, cancelled,
        paid - refunded, payments, payments == 0 ? null : Math.round((double) paid / payments),
        number(debt.get("debt")), number(debt.get("unpaid")), open, refunded,
        number(patients.get("newPatients")), number(patients.get("returningPatients")),
        number(retention.get("completed")), number(retention.get("withoutNext")),
        occupied, available,
        number(waitlist.get("active")), number(waitlist.get("booked")), number(waitlist.get("periodTotal")));

    return new AnalyticsOverviewView(
        from, to, "RUB", totals, weeks, capacity.practitioners(), capacity.rooms(),
        nextSlots(practitioners, shifts, practitionerId), funnel(from, to, practitionerId),
        queues(practitionerId, number(debt.get("unpaid"))));
  }

  /** Для виджета загрузки на home: внутренний расчёт без повторной проверки права аналитики. */
  Occupancy occupancy(LocalDate from, LocalDate to, UUID practitionerId) {
    var capacity = capacity(from, to, practitioners(practitionerId), shifts(practitionerId), appointments(from, to, practitionerId));
    return new Occupancy(
        capacity.practitioners().stream().mapToLong(Load::occupiedMinutes).sum(),
        capacity.practitioners().stream().mapToLong(Load::availableMinutes).sum());
  }

  record Occupancy(long occupiedMinutes, long availableMinutes) {}

  private void validateRange(LocalDate from, LocalDate to) {
    if (from == null || to == null || to.isBefore(from)) {
      throw conflict("INVALID_RANGE", "Period end must not be before its start");
    }
    if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
      throw conflict("RANGE_TOO_LONG", "Period must not exceed one year");
    }
  }

  private List<PractitionerRow> practitioners(UUID practitionerId) {
    var sql = "select id, full_name, specialty, coalesce(room, '') as room from practitioner"
        + " where clinic_id = ?" + (practitionerId == null ? "" : " and id = ?") + " order by full_name";
    return jdbc.query(sql, (rs, n) -> new PractitionerRow(
        UUID.fromString(rs.getString("id")), rs.getString("full_name"), rs.getString("specialty"), rs.getString("room")),
        args(practitionerId, clinicId()));
  }

  private List<ShiftRow> shifts(UUID practitionerId) {
    var sql = "select s.practitioner_id, s.day_of_week, s.starts_at, s.ends_at"
        + " from practitioner_schedule s join practitioner p on p.id = s.practitioner_id"
        + " where p.clinic_id = ?" + (practitionerId == null ? "" : " and s.practitioner_id = ?");
    return jdbc.query(sql, (rs, n) -> new ShiftRow(
        UUID.fromString(rs.getString("practitioner_id")), rs.getInt("day_of_week"),
        rs.getTime("starts_at").toLocalTime(), rs.getTime("ends_at").toLocalTime()),
        args(practitionerId, clinicId()));
  }

  private List<AppointmentRow> appointments(LocalDate from, LocalDate to, UUID practitionerId) {
    var sql = "select a.patient_id, a.practitioner_id, a.starts_at, a.ends_at, a.status"
        + " from appointment a join practitioner p on p.id = a.practitioner_id"
        + " where p.clinic_id = ? and a.starts_at >= ? and a.starts_at < ?"
        + (practitionerId == null ? "" : " and a.practitioner_id = ?");
    return jdbc.query(sql, (rs, n) -> new AppointmentRow(
        UUID.fromString(rs.getString("patient_id")), UUID.fromString(rs.getString("practitioner_id")),
        rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(), rs.getString("status")),
        args(practitionerId, clinicId(), Timestamp.from(time.startOf(from)), Timestamp.from(time.startOf(to.plusDays(1)))));
  }

  private List<FinanceRow> finance(LocalDate from, LocalDate to, UUID practitionerId) {
    var start = Timestamp.from(time.startOf(from));
    var end = Timestamp.from(time.startOf(to.plusDays(1)));
    var filter = practitionerId == null ? "" : " and a.practitioner_id = ?";
    var result = new ArrayList<FinanceRow>();
    result.addAll(jdbc.query(
        "select coalesce(r.completed_at, r.created_at) as at, c.amount_minor as amount, c.price_mode as mode"
            + " from payment_record r join charge c on c.id = r.charge_id"
            + " join appointment a on a.id = c.appointment_id join practitioner p on p.id = a.practitioner_id"
            + " where p.clinic_id = ? and coalesce(r.completed_at, r.created_at) >= ?"
            + " and coalesce(r.completed_at, r.created_at) < ?"
            + " and r.payment_status in ('SUCCEEDED', 'MOCK_NO_PROVIDER', 'CONFIRMED')" + filter,
        (rs, n) -> new FinanceRow(rs.getTimestamp("at").toInstant(), rs.getLong("amount"), rs.getString("mode"), false),
        args(practitionerId, clinicId(), start, end)));
    result.addAll(jdbc.query(
        "select f.created_at as at, f.amount_minor as amount, c.price_mode as mode"
            + " from refund_record f join charge c on c.id = f.charge_id"
            + " join appointment a on a.id = c.appointment_id join practitioner p on p.id = a.practitioner_id"
            + " where p.clinic_id = ? and f.created_at >= ? and f.created_at < ?" + filter,
        (rs, n) -> new FinanceRow(rs.getTimestamp("at").toInstant(), rs.getLong("amount"), rs.getString("mode"), true),
        args(practitionerId, clinicId(), start, end)));
    return result;
  }

  private Map<String, Object> debt(UUID practitionerId) {
    var filter = practitionerId == null ? "" : " and a.practitioner_id = ?";
    return jdbc.queryForMap(
        "select coalesce(sum(case when c.status = 'PENDING' then c.amount_minor else 0 end), 0) as debt,"
            + " count(*) as unpaid from charge c join appointment a on a.id = c.appointment_id"
            + " join practitioner p on p.id = a.practitioner_id"
            + " where p.clinic_id = ? and c.status in ('PENDING', 'AWAITING_AMOUNT')" + filter,
        args(practitionerId, clinicId()));
  }

  private Map<String, Object> patientMetrics(LocalDate from, LocalDate to, UUID practitionerId) {
    var start = Timestamp.from(time.startOf(from));
    var end = Timestamp.from(time.startOf(to.plusDays(1)));
    var filter = practitionerId == null ? "" : " and a.practitioner_id = ?";
    long total = count(
        "select count(distinct a.patient_id) from appointment a join practitioner p on p.id = a.practitioner_id"
            + " where p.clinic_id = ? and a.starts_at >= ? and a.starts_at < ?" + filter,
        args(practitionerId, clinicId(), start, end));
    long newPatients = count(
        "select count(distinct a.patient_id) from appointment a join practitioner p on p.id = a.practitioner_id"
            + " where p.clinic_id = ? and a.starts_at >= ? and a.starts_at < ?"
            + " and not exists (select 1 from appointment earlier where earlier.patient_id = a.patient_id"
            + " and earlier.starts_at < ?)" + filter,
        args(practitionerId, clinicId(), start, end, start));
    return Map.of("newPatients", newPatients, "returningPatients", total - newPatients);
  }

  private Map<String, Object> retention(LocalDate from, LocalDate to, UUID practitionerId) {
    var start = Timestamp.from(time.startOf(from));
    var end = Timestamp.from(time.startOf(to.plusDays(1)));
    var filter = practitionerId == null ? "" : " and a.practitioner_id = ?";
    long completed = count(
        "select count(distinct a.patient_id) from appointment a join practitioner p on p.id = a.practitioner_id"
            + " where p.clinic_id = ? and a.status = 'COMPLETED' and a.starts_at >= ? and a.starts_at < ?" + filter,
        args(practitionerId, clinicId(), start, end));
    long withoutNext = count(
        "select count(distinct a.patient_id) from appointment a join practitioner p on p.id = a.practitioner_id"
            + " where p.clinic_id = ? and a.status = 'COMPLETED' and a.starts_at >= ? and a.starts_at < ?"
            + " and not exists (select 1 from appointment later where later.patient_id = a.patient_id"
            + " and later.starts_at > a.starts_at and later.status <> 'CANCELLED')" + filter,
        args(practitionerId, clinicId(), start, end));
    return Map.of("completed", completed, "withoutNext", withoutNext);
  }

  private Map<String, Object> waitlist(LocalDate from, LocalDate to, UUID practitionerId) {
    var filter = practitionerId == null ? "" : " and w.practitioner_id = ?";
    long active = count(
        "select count(*) from waitlist_entry w join practitioner p on p.id = w.practitioner_id"
            + " where p.clinic_id = ? and w.status = 'ACTIVE'" + filter,
        args(practitionerId, clinicId()));
    var period = jdbc.queryForMap(
        "select count(*) as total, coalesce(sum(case when w.status = 'BOOKED' then 1 else 0 end), 0) as booked"
            + " from waitlist_entry w join practitioner p on p.id = w.practitioner_id"
            + " where p.clinic_id = ? and w.created_at >= ? and w.created_at < ?" + filter,
        args(practitionerId, clinicId(), Timestamp.from(time.startOf(from)), Timestamp.from(time.startOf(to.plusDays(1)))));
    return Map.of("active", active, "booked", number(period.get("booked")), "periodTotal", number(period.get("total")));
  }

  private Funnel funnel(LocalDate from, LocalDate to, UUID practitionerId) {
    var own = practitionerId == null ? "" : " and exists (select 1 from appointment own where own.patient_id = p.id and own.practitioner_id = ?)";
    var row = jdbc.queryForMap(
        "select count(*) as contacts,"
            + " coalesce(sum(case when exists (select 1 from appointment a where a.patient_id = p.id) then 1 else 0 end), 0) as booked,"
            + " coalesce(sum(case when exists (select 1 from appointment a where a.patient_id = p.id and a.status = 'COMPLETED') then 1 else 0 end), 0) as visited,"
            + " coalesce(sum(case when exists (select 1 from charge c join appointment a on a.id = c.appointment_id"
            + " where a.patient_id = p.id and c.status in ('PAID', 'REFUNDED')) then 1 else 0 end), 0) as paid"
            + " from patient p where p.clinic_id = ? and p.created_at >= ? and p.created_at < ?" + own,
        args(practitionerId, clinicId(), Timestamp.from(time.startOf(from)), Timestamp.from(time.startOf(to.plusDays(1)))));
    return new Funnel(number(row.get("contacts")), number(row.get("booked")), number(row.get("visited")), number(row.get("paid")));
  }

  private Queues queues(UUID practitionerId, long unpaid) {
    var filter = practitionerId == null ? "" : " and e.practitioner_id = ?";
    long unclosed = count(
        "select count(*) from encounter e join practitioner p on p.id = e.practitioner_id"
            + " where p.clinic_id = ? and e.completed_at is null" + filter,
        args(practitionerId, clinicId()));
    long unsigned = count(
        "select count(*) from medical_document d join encounter e on e.id = d.encounter_id"
            + " join practitioner p on p.id = e.practitioner_id"
            + " where p.clinic_id = ? and d.state = 'DRAFT'" + filter,
        args(practitionerId, clinicId()));
    if (practitionerId == null) {
      unsigned += count("select count(*) from patient_file where clinic_id = ? and state = 'DRAFT'", clinicId());
    } else {
      unsigned += count(
          "select count(*) from patient_file f join appointment a on a.id = f.appointment_id"
              + " where f.clinic_id = ? and f.state = 'DRAFT' and a.practitioner_id = ?",
          clinicId(), practitionerId);
    }
    return new Queues(unclosed, unsigned, unpaid);
  }

  private Capacity capacity(
      LocalDate from, LocalDate to, List<PractitionerRow> practitioners, List<ShiftRow> shifts, List<AppointmentRow> appointments) {
    var doctorResources = practitioners.stream()
        .map(p -> new Resource(p.id().toString(), p.fullName(), p.specialty(), Set.of(p.id())))
        .toList();
    var byRoom = practitioners.stream().filter(p -> !p.room().isBlank())
        .collect(Collectors.groupingBy(PractitionerRow::room, LinkedHashMap::new, Collectors.toList()));
    var roomResources = byRoom.entrySet().stream()
        .map(e -> new Resource("room:" + e.getKey(), e.getKey(), null,
            e.getValue().stream().map(PractitionerRow::id).collect(Collectors.toSet())))
        .toList();
    var weekCapacity = new LinkedHashMap<LocalDate, MutableWeekCapacity>();
    for (var monday = monday(from); !monday.isAfter(to); monday = monday.plusWeeks(1)) {
      weekCapacity.put(monday, new MutableWeekCapacity());
    }
    var doctorLoads = loads(from, to, doctorResources, shifts, appointments, weekCapacity);
    var roomLoads = loads(from, to, roomResources, shifts, appointments, null);
    return new Capacity(doctorLoads, roomLoads, weekCapacity);
  }

  private List<Load> loads(
      LocalDate from, LocalDate to, List<Resource> resources, List<ShiftRow> shifts,
      List<AppointmentRow> appointments, Map<LocalDate, MutableWeekCapacity> weeks) {
    var result = new ArrayList<Load>();
    for (var resource : resources) {
      long available = 0;
      long occupied = 0;
      for (var date = from; !date.isAfter(to); date = date.plusDays(1)) {
        var day = date;
        var availableIntervals = shifts.stream()
            .filter(s -> resource.practitioners().contains(s.practitionerId()))
            .filter(s -> s.dayOfWeek() == day.getDayOfWeek().getValue())
            .map(s -> new Interval(s.startsAt().toSecondOfDay() / 60, s.endsAt().toSecondOfDay() / 60))
            .toList();
        var occupiedIntervals = appointments.stream()
            .filter(a -> resource.practitioners().contains(a.practitionerId()))
            .filter(a -> !"CANCELLED".equals(a.status()))
            .filter(a -> LocalDate.ofInstant(a.startsAt(), time.zone()).equals(day))
            .map(a -> new Interval(
                a.startsAt().atZone(time.zone()).toLocalTime().toSecondOfDay() / 60,
                a.endsAt().atZone(time.zone()).toLocalTime().toSecondOfDay() / 60))
            .toList();
        long dayAvailable = unionMinutes(availableIntervals);
        long dayOccupied = unionMinutes(occupiedIntervals);
        available += dayAvailable;
        occupied += dayOccupied;
        if (weeks != null) {
          var week = weeks.get(monday(day));
          if (week != null) {
            week.available += dayAvailable;
            week.occupied += dayOccupied;
          }
        }
      }
      result.add(new Load(resource.id(), resource.label(), resource.specialty(), occupied, available));
    }
    return result;
  }

  private List<Week> weekly(
      LocalDate from, LocalDate to, List<AppointmentRow> appointments, List<FinanceRow> finance,
      Map<LocalDate, MutableWeekCapacity> capacity) {
    var weeks = new LinkedHashMap<LocalDate, MutableWeek>();
    for (var monday = monday(from); !monday.isAfter(to); monday = monday.plusWeeks(1)) {
      weeks.put(monday, new MutableWeek());
    }
    for (var appointment : appointments) {
      var week = weeks.get(monday(LocalDate.ofInstant(appointment.startsAt(), time.zone())));
      if (week == null) continue;
      week.appointments++;
      if (attended(appointment.status())) week.attended++;
      if ("NO_SHOW".equals(appointment.status())) week.noShow++;
      if ("CANCELLED".equals(appointment.status())) week.cancelled++;
    }
    for (var row : finance) {
      var week = weeks.get(monday(LocalDate.ofInstant(row.at(), time.zone())));
      if (week != null) week.revenue += row.refund() ? -row.amount() : row.amount();
    }
    return weeks.entrySet().stream().map(e -> {
      var week = e.getValue();
      var load = capacity.get(e.getKey());
      return new Week(e.getKey(), week.appointments, week.attended, week.noShow, week.cancelled,
          week.revenue, load == null ? 0 : load.occupied, load == null ? 0 : load.available);
    }).toList();
  }

  private List<NextSlot> nextSlots(List<PractitionerRow> practitioners, List<ShiftRow> shifts, UUID practitionerId) {
    var now = time.now();
    var today = LocalDate.ofInstant(now, time.zone());
    var future = appointments(today, today.plusDays(NEXT_SLOT_DAYS), practitionerId);
    var slots = new HashMap<String, List<Instant>>();
    for (var practitioner : practitioners) {
      var slot = nextSlot(practitioner.id(), now, shifts, future);
      if (slot != null) {
        slots.computeIfAbsent(practitioner.specialty().isBlank() ? "Без специальности" : practitioner.specialty(), k -> new ArrayList<>()).add(slot.toInstant());
      }
    }
    return slots.entrySet().stream().map(e -> {
      var nearest = e.getValue().stream().min(Comparator.naturalOrder()).orElseThrow();
      var average = e.getValue().stream().mapToLong(slot -> Duration.between(now, slot).toMinutes()).average().orElse(0) / 60;
      return new NextSlot(e.getKey(), e.getValue().size(), Math.round(average * 10) / 10.0, nearest);
    }).sorted(Comparator.comparing(NextSlot::specialty)).toList();
  }

  private ZonedDateTime nextSlot(UUID practitionerId, Instant now, List<ShiftRow> shifts, List<AppointmentRow> appointments) {
    var zone = time.zone();
    var current = now.atZone(zone);
    var ownShifts = shifts.stream().filter(s -> s.practitionerId().equals(practitionerId)).toList();
    var ownAppointments = appointments.stream()
        .filter(a -> a.practitionerId().equals(practitionerId) && !"CANCELLED".equals(a.status())).toList();
    for (var date = current.toLocalDate(); !date.isAfter(current.toLocalDate().plusDays(NEXT_SLOT_DAYS)); date = date.plusDays(1)) {
      for (var shift : ownShifts) {
        if (shift.dayOfWeek() != date.getDayOfWeek().getValue()) continue;
        var candidate = date.atTime(shift.startsAt()).atZone(zone);
        if (date.equals(current.toLocalDate()) && candidate.isBefore(current)) candidate = quarterHourAfter(current);
        var end = date.atTime(shift.endsAt()).atZone(zone);
        while (!candidate.plusMinutes(NEXT_SLOT_MINUTES).isAfter(end)) {
          var start = candidate.toInstant();
          var finish = candidate.plusMinutes(NEXT_SLOT_MINUTES).toInstant();
          var overlaps = ownAppointments.stream().anyMatch(a -> start.isBefore(a.endsAt()) && finish.isAfter(a.startsAt()));
          if (!overlaps && !start.isBefore(now)) return candidate;
          candidate = candidate.plusMinutes(15);
        }
      }
    }
    return null;
  }

  private static ZonedDateTime quarterHourAfter(ZonedDateTime value) {
    var truncated = value.withSecond(0).withNano(0);
    var add = (15 - truncated.getMinute() % 15) % 15;
    if (add == 0 && !truncated.equals(value)) add = 15;
    return truncated.plusMinutes(add);
  }

  private static boolean attended(String status) {
    return Set.of("ARRIVED", "IN_PROGRESS", "COMPLETED").contains(status);
  }

  private static LocalDate monday(LocalDate date) {
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  /** Длительность занятого/доступного времени без двойного счёта пересекающихся интервалов. */
  static long unionMinutes(List<Interval> intervals) {
    var sorted = intervals.stream().filter(i -> i.end() > i.start())
        .sorted(Comparator.comparingLong(Interval::start)).toList();
    long total = 0;
    long start = -1;
    long end = -1;
    for (var interval : sorted) {
      if (end < 0 || interval.start() > end) {
        if (end >= 0) total += end - start;
        start = interval.start();
        end = interval.end();
      } else {
        end = Math.max(end, interval.end());
      }
    }
    return end < 0 ? 0 : total + end - start;
  }

  private long count(String sql, Object... args) {
    var value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
  }

  private static long number(Object value) {
    return value == null ? 0 : ((Number) value).longValue();
  }

  private static Object[] args(UUID practitionerId, Object... head) {
    if (practitionerId == null) return head;
    var result = new Object[head.length + 1];
    System.arraycopy(head, 0, result, 0, head.length);
    result[head.length] = practitionerId;
    return result;
  }

  private record PractitionerRow(UUID id, String fullName, String specialty, String room) {}
  private record ShiftRow(UUID practitionerId, int dayOfWeek, LocalTime startsAt, LocalTime endsAt) {}
  private record AppointmentRow(UUID patientId, UUID practitionerId, Instant startsAt, Instant endsAt, String status) {}
  private record FinanceRow(Instant at, long amount, String priceMode, boolean refund) {}
  private record Resource(String id, String label, String specialty, Set<UUID> practitioners) {}
  record Interval(long start, long end) {}
  private record Capacity(List<Load> practitioners, List<Load> rooms, Map<LocalDate, MutableWeekCapacity> weeks) {}

  private static final class MutableWeek {
    long appointments;
    long attended;
    long noShow;
    long cancelled;
    long revenue;
  }

  private static final class MutableWeekCapacity {
    long occupied;
    long available;
  }
}
