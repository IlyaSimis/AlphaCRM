package ru.quietclinic.insights;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.can;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.currentRoles;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;
import static ru.quietclinic.platform.PlatformConfig.staffId;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.insights.DashboardView.Widget;
import ru.quietclinic.platform.ClinicTime;
import ru.quietclinic.platform.Permission;

/** Виджеты персонального дашборда по разрешениям: врач — свои приёмы, остальные — по клинике. */
@Service
class DashboardService {
  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  private final JdbcTemplate jdbc;
  private final ClinicTime time;
  private final AnalyticsOverviewService analytics;

  DashboardService(JdbcTemplate jdbc, ClinicTime time, AnalyticsOverviewService analytics) {
    this.jdbc = jdbc;
    this.time = time;
    this.analytics = analytics;
  }

  @Transactional(readOnly = true)
  DashboardView dashboard() {
    requirePermission(Permission.DASHBOARD_READ);
    var today = time.today();
    var monthStart = today.withDayOfMonth(1);
    var from = Timestamp.from(time.startOf(monthStart));
    var to = Timestamp.from(time.startOf(monthStart.plusMonths(1)));
    var dayFrom = Timestamp.from(time.startOf(today));
    var dayTo = Timestamp.from(time.startOf(today.plusDays(1)));
    // Врач видит свои приёмы; аналитика клиники — у тех, кому она разрешена.
    var own = !can(Permission.ANALYTICS_READ) && can(Permission.APPOINTMENT_WRITE_OWN);
    var scope = own ? "OWN" : "CLINIC";
    var widgets = new ArrayList<Widget>();

    if (can(Permission.SCHEDULE_READ) && (can(Permission.ANALYTICS_READ) || can(Permission.ANALYTICS_READ_OWN))) {
      var filter = appointmentFilter(own);
      var month =
          jdbc.queryForMap(
              "select count(*) as total, coalesce(sum(case when a.status = 'COMPLETED' then 1 else"
                  + " 0 end), 0) as done, coalesce(sum(case when a.status = 'NO_SHOW' then 1 else 0"
                  + " end), 0) as missed, coalesce(sum(case when a.status = 'CANCELLED' then 1 else"
                  + " 0 end), 0) as cancelled from appointment a join practitioner p on p.id ="
                  + " a.practitioner_id where p.clinic_id = ? and a.starts_at >= ? and a.starts_at"
                  + " < ?"
                  + filter.sql(),
              filter.args(clinicId(), from, to));
      widgets.add(
          new Widget(
              "appointments.month",
              scope,
              number(month.get("total")),
              number(month.get("done")),
              "count",
              List.of()));
      widgets.add(
          new Widget(
              "appointments.month.noShow",
              scope,
              number(month.get("missed")),
              number(month.get("cancelled")),
              "count",
              List.of()));
      var upcoming =
          jdbc.queryForList(
              "select a.starts_at from appointment a join practitioner p on p.id ="
                  + " a.practitioner_id where p.clinic_id = ? and a.starts_at >= ? and a.starts_at"
                  + " < ? and a.status in ('BOOKED', 'CONFIRMED', 'ARRIVED', 'IN_PROGRESS')"
                  + filter.sql()
                  + " order by a.starts_at",
              Timestamp.class,
              filter.args(clinicId(), dayFrom, dayTo));
      var now = Instant.now();
      var next =
          upcoming.stream()
              .map(Timestamp::toInstant)
              .filter(t -> !t.isBefore(now.minusSeconds(30 * 60)))
              .limit(5)
              .map(t -> HH_MM.format(t.atZone(time.zone())))
              .toList();
      widgets.add(new Widget("appointments.today", scope, upcoming.size(), null, "count", next));
      var load = analytics.occupancy(monthStart, monthStart.plusMonths(1).minusDays(1), own ? actorId() : null);
      long loadPercent = load.availableMinutes() == 0 ? 0 : Math.round(load.occupiedMinutes() * 100.0 / load.availableMinutes());
      widgets.add(new Widget("schedule.load", scope, loadPercent, load.occupiedMinutes(), "percent", List.of()));
    }

    if (can(Permission.CLINICAL_RECORD)) {
      var drafts =
          count(
              "select count(*) from medical_document d join encounter e on e.id = d.encounter_id"
                  + " where e.practitioner_id = ? and d.state = 'DRAFT'",
              actorId());
      var files =
          count(
              "select count(*) from patient_file where clinic_id = ? and uploaded_by = ?"
                  + " and state = 'DRAFT'",
              clinicId(),
              staffId());
      widgets.add(
          new Widget("documents.unsigned", "OWN", drafts + files, null, "count", List.of()));
    }

    // Экстренные доступы за 7 дней (K1): старшему администратору — по клинике, врачу — к его
    // пациентам (по действующей связи с лечением). Только счётчик, без причин.
    var weekAgo = Timestamp.from(time.now().minus(java.time.Duration.ofDays(7)));
    if (can(Permission.CLINICAL_BREAK_GLASS_REVIEW)) {
      var granted =
          count(
              "select count(*) from break_glass_grant where clinic_id = ? and granted_at >= ?",
              clinicId(),
              weekAgo);
      widgets.add(new Widget("clinical.break-glass", "CLINIC", granted, null, "count", List.of()));
    } else if (can(Permission.CLINICAL_RECORD)) {
      var granted =
          count(
              "select count(*) from break_glass_grant g where g.clinic_id = ? and g.granted_at >= ?"
                  + " and exists (select 1 from care_relationship r where r.patient_id ="
                  + " g.patient_id and r.practitioner_id = ? and r.valid_until > ?)",
              clinicId(),
              weekAgo,
              actorId(),
              weekAgo);
      widgets.add(new Widget("clinical.break-glass", "OWN", granted, null, "count", List.of()));
    }

    if (can(Permission.INVOICE_READ)) {
      var unpaid =
          jdbc.queryForMap(
              "select count(*) as cnt, coalesce(sum(c.amount_minor), 0) as total from charge c"
                  + " join appointment a on a.id = c.appointment_id"
                  + " join practitioner p on p.id = a.practitioner_id"
                  + " where p.clinic_id = ? and c.status = 'PENDING'",
              clinicId());
      widgets.add(
          new Widget(
              "charges.unpaid",
              "CLINIC",
              number(unpaid.get("cnt")),
              number(unpaid.get("total")),
              "money",
              List.of()));
      widgets.add(
          new Widget(
              "charges.awaitingAmount",
              "CLINIC",
              count(
                  "select count(*) from charge c join appointment a on a.id = c.appointment_id"
                      + " join practitioner p on p.id = a.practitioner_id"
                      + " where p.clinic_id = ? and c.status = 'AWAITING_AMOUNT'",
                  clinicId()),
              null,
              "count",
              List.of()));
    }

    if (can(Permission.REVENUE_READ)) {
      var revenue =
          jdbc.queryForMap(
              "select count(*) as cnt, coalesce(sum(c.amount_minor), 0) as total"
                  + " from payment_record r join charge c on c.id = r.charge_id"
                  + " join appointment a on a.id = c.appointment_id"
                  + " join practitioner p on p.id = a.practitioner_id"
                  + " where p.clinic_id = ? and r.created_at >= ? and r.created_at < ?",
              clinicId(),
              from,
              to);
      // Возвраты месяца вычитаются: выручка — оплаты минус возвраты (раздел D4).
      var refunded =
          jdbc.queryForObject(
              "select coalesce(sum(f.amount_minor), 0) from refund_record f"
                  + " join charge c on c.id = f.charge_id"
                  + " join appointment a on a.id = c.appointment_id"
                  + " join practitioner p on p.id = a.practitioner_id"
                  + " where p.clinic_id = ? and f.created_at >= ? and f.created_at < ?",
              Long.class,
              clinicId(),
              from,
              to);
      widgets.add(
          new Widget(
              "revenue.month",
              "CLINIC",
              number(revenue.get("total")) - (refunded == null ? 0 : refunded),
              number(revenue.get("cnt")),
              "money",
              List.of()));
    }

    if (can(Permission.HR_READ_OWN) || can(Permission.HR_MANAGE)) {
      widgets.add(salaryWidget(monthStart, today, from, to));
    }

    if (can(Permission.WORKSPACE_USE)) {
      widgets.add(
          new Widget(
              "workspace.due",
              "OWN",
              count(
                  "select count(*) from workspace.item where clinic_id = ? and owner_id = ?"
                      + " and completed = false and archived_at is null and due_at <= ?",
                  clinicId(),
                  staffId(),
                  Timestamp.from(time.startOf(today.plusDays(1)))),
              count(
                  "select count(*) from workspace.item where clinic_id = ? and owner_id = ?"
                      + " and completed = false and archived_at is null and due_at < ?",
                  clinicId(),
                  staffId(),
                  Timestamp.from(time.startOf(today))),
              "count",
              List.of()));
    }

    if (can(Permission.REGISTRY_TASK_READ)) {
      var mine = !can(Permission.REGISTRY_TASK_MANAGE);
      widgets.add(
          new Widget(
              "tasks.due",
              mine ? "OWN" : "CLINIC",
              mine
                  ? count(
                      "select count(*) from registry_task where clinic_id = ? and status = 'OPEN'"
                          + " and due_date <= ? and assignee_id in (?, ?)",
                      clinicId(),
                      Date.valueOf(today),
                      staffId(),
                      actorId())
                  : count(
                      "select count(*) from registry_task where clinic_id = ? and status = 'OPEN'"
                          + " and due_date <= ?",
                      clinicId(),
                      Date.valueOf(today)),
              null,
              "count",
              List.of()));
    }

    if (can(Permission.INVENTORY_STOCK_READ)) {
      widgets.add(
          new Widget(
              "stock.shortage",
              "CLINIC",
              count(
                  "select count(*) from inventory.item i left join (select item_id, sum(quantity) as quantity"
                      + " from inventory.stock_balance where clinic_id = ? group by item_id) b on b.item_id = i.id"
                      + " where i.clinic_id = ? and i.active = true and coalesce(b.quantity, 0) < i.min_stock",
                  clinicId(),
                  clinicId()),
              null,
              "count",
              List.of()));
      widgets.add(
          new Widget(
              "stock.expiring",
              "CLINIC",
              count(
                  "select count(*) from inventory.stock_balance where clinic_id = ? and quantity >"
                      + " 0 and expires_on is not null and expires_on <= ?",
                  clinicId(),
                  Date.valueOf(today.plusDays(30))),
              null,
              "count",
              List.of()));
    }

    if (can(Permission.MARKING_STATUS_READ)) {
      widgets.add(
          new Widget(
              "marking.queue",
              "CLINIC",
              count(
                  "select count(*) from inventory.marking_operation where clinic_id = ?"
                      + " and status = 'PENDING'",
                  clinicId()),
              count(
                  "select count(*) from inventory.marking_operation where clinic_id = ?"
                      + " and status = 'ERROR'",
                  clinicId()),
              "count",
              List.of()));
    }

    if (can(Permission.DELIVERY_READ)) {
      widgets.add(
          new Widget(
              "delivery.failed",
              "CLINIC",
              count(
                  "select count(*) from outbox_message where clinic_id = ? and status = 'FAILED'",
                  clinicId()),
              count(
                  "select count(*) from outbox_message where clinic_id = ? and status = 'PENDING'",
                  clinicId()),
              "count",
              List.of()));
    }

    if (can(Permission.AUDIT_READ_PLATFORM) || can(Permission.DELIVERY_RETRY)) {
      widgets.add(
          new Widget(
              "events.backlog",
              "CLINIC",
              count(
                  "select count(*) from domain_event_outbox where clinic_id = ? and status ="
                      + " 'PENDING' and attempts > 0",
                  clinicId()),
              count(
                  "select count(*) from domain_event_outbox where clinic_id = ? and status ="
                      + " 'FAILED'",
                  clinicId()),
              "count",
              List.of()));
    }

    if (can(Permission.DRIVE_ADMIN)) {
      var quota =
          jdbc.queryForList(
              "select bytes_used, bytes_limit from drive.drive_quota where clinic_id = ?",
              clinicId());
      var used = quota.isEmpty() ? 0 : number(quota.get(0).get("bytes_used"));
      var limit = quota.isEmpty() ? null : number(quota.get(0).get("bytes_limit"));
      widgets.add(new Widget("drive.quota", "CLINIC", used, limit, "bytes", List.of()));
    }

    var role = currentRoles().stream().findFirst().orElse(null);
    return new DashboardView(role, monthStart, today, Instant.now(), widgets);
  }

  /**
   * Расчётная зарплата сотрудника, а не платёжная ведомость: месячный оклад, часы по месячному
   * графику или оплата завершённых приёмов. Чужие ставки в общий дашборд не попадают.
   */
  private Widget salaryWidget(
      java.time.LocalDate monthStart, java.time.LocalDate today, Timestamp from, Timestamp to) {
    var contracts =
        jdbc.queryForList(
            "select rate_minor, rate_unit from hr_contract where clinic_id = ? and staff_id = ?"
                + " and valid_from <= ? and (valid_to is null or valid_to >= ?)"
                + " order by valid_from desc limit 1",
            clinicId(),
            staffId(),
            Date.valueOf(today),
            Date.valueOf(today));
    if (contracts.isEmpty()) {
      return new Widget("salary.month", "OWN", 0, null, "money", List.of());
    }
    var contract = contracts.get(0);
    var rate = number(contract.get("rate_minor"));
    var unit = String.valueOf(contract.get("rate_unit"));
    long value;
    if ("MONTH".equals(unit)) {
      value = rate;
    } else if ("VISIT".equals(unit)) {
      var completed =
          count(
              "select count(*) from appointment a join practitioner p on p.id = a.practitioner_id"
                  + " where p.clinic_id = ? and a.practitioner_id = ? and a.status = 'COMPLETED'"
                  + " and a.starts_at >= ? and a.starts_at < ?",
              clinicId(),
              actorId(),
              from,
              to);
      value = Math.multiplyExact(rate, completed);
    } else {
      var shifts =
          jdbc.queryForList(
              "select s.day_of_week, s.starts_at, s.ends_at from practitioner_schedule s"
                  + " join staff_user u on u.practitioner_id = s.practitioner_id"
                  + " where u.clinic_id = ? and u.id = ?",
              clinicId(),
              staffId());
      long minutes = 0;
      for (var day = monthStart; day.isBefore(monthStart.plusMonths(1)); day = day.plusDays(1)) {
        for (var shift : shifts) {
          if (number(shift.get("day_of_week")) == day.getDayOfWeek().getValue()) {
            var start = ((java.sql.Time) shift.get("starts_at")).toLocalTime();
            var end = ((java.sql.Time) shift.get("ends_at")).toLocalTime();
            minutes += java.time.Duration.between(start, end).toMinutes();
          }
        }
      }
      value = Math.multiplyExact(rate, minutes) / 60;
    }
    return new Widget("salary.month", "OWN", value, null, "money", List.of(unit));
  }

  /** Фильтр «только свои приёмы» для врача. */
  private Filter appointmentFilter(boolean own) {
    return own ? new Filter(" and a.practitioner_id = ?", actorId()) : new Filter("", null);
  }

  private record Filter(String sql, UUID practitionerId) {
    Object[] args(Object... head) {
      if (practitionerId == null) return head;
      var all = new Object[head.length + 1];
      System.arraycopy(head, 0, all, 0, head.length);
      all[head.length] = practitionerId;
      return all;
    }
  }

  private long count(String sql, Object... args) {
    var value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
  }

  private static long number(Object value) {
    return value == null ? 0 : ((Number) value).longValue();
  }
}
