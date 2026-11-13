package ru.quietclinic.registry;

import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.platform.ClinicTime;
import ru.quietclinic.platform.Permission;

/** Очередь конкретных действий регистратуры: подтвердить, оплатить, записать снова или связаться. */
@Service
class RegistryWorkQueueService {
  private final JdbcTemplate jdbc;
  private final ClinicTime time;

  RegistryWorkQueueService(JdbcTemplate jdbc, ClinicTime time) {
    this.jdbc = jdbc;
    this.time = time;
  }

  @Transactional(readOnly = true)
  RegistryWorkQueueView queue(LocalDate requestedDate) {
    requirePermission(Permission.REGISTRY_TASK_MANAGE);
    LocalDate date = requestedDate == null ? time.today() : requestedDate;
    Timestamp start = Timestamp.from(time.startOf(date));
    Timestamp end = Timestamp.from(time.startOf(date.plusDays(1)));
    List<RegistryWorkQueueView.Item> items = new ArrayList<>();
    List<AppointmentRow> appointments = appointments(start, end);
    for (AppointmentRow appointment : appointments) {
      if ("BOOKED".equals(appointment.status())) {
        items.add(item(appointment, "CONFIRMATION", null, null, "Запись ещё не подтверждена"));
      }
      if ("COMPLETED".equals(appointment.status())
          && Set.of("PENDING", "AWAITING_AMOUNT").contains(appointment.chargeStatus())) {
        items.add(
            item(
                appointment,
                "PAYMENT",
                appointment.chargeId(),
                appointment.amountMinor(),
                "Есть неоплаченный счёт"));
      }
      if ("COMPLETED".equals(appointment.status()) && appointment.withoutNext()) {
        items.add(item(appointment, "REBOOK", null, null, "Следующего визита нет"));
      }
      if ("NO_SHOW".equals(appointment.status())) {
        items.add(item(appointment, "NO_SHOW", null, null, "Нужно связаться с пациентом"));
      }
    }
    items.addAll(failedDeliveries(start, end));
    items.sort(
        Comparator.comparing(RegistryWorkQueueView.Item::startsAt)
            .thenComparing(RegistryWorkQueueView.Item::kind)
            .thenComparing(RegistryWorkQueueView.Item::id));
    return new RegistryWorkQueueView(date, List.copyOf(items));
  }

  private List<AppointmentRow> appointments(Timestamp start, Timestamp end) {
    return jdbc.query(
        "select a.id, a.patient_id, patient.full_name as patient_name, a.practitioner_id,"
            + " practitioner.full_name as practitioner_name, a.starts_at, a.service_name, a.status,"
            + " c.id as charge_id, c.amount_minor, coalesce(c.currency, a.currency) as currency,"
            + " c.status as charge_status,"
            + " case when not exists (select 1 from appointment later"
            + " where later.patient_id = a.patient_id and later.starts_at > a.starts_at"
            + " and later.status <> 'CANCELLED') then true else false end as without_next"
            + " from appointment a"
            + " join patient on patient.id = a.patient_id"
            + " join practitioner on practitioner.id = a.practitioner_id"
            + " left join charge c on c.appointment_id = a.id"
            + " where practitioner.clinic_id = ? and a.starts_at >= ? and a.starts_at < ?"
            + " order by a.starts_at",
        (rs, rowNumber) ->
            new AppointmentRow(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("patient_id")),
                rs.getString("patient_name"),
                UUID.fromString(rs.getString("practitioner_id")),
                rs.getString("practitioner_name"),
                rs.getTimestamp("starts_at").toInstant(),
                rs.getString("service_name"),
                rs.getString("status"),
                rs.getString("charge_id") == null ? null : UUID.fromString(rs.getString("charge_id")),
                rs.getObject("amount_minor") == null ? null : rs.getLong("amount_minor"),
                rs.getString("currency"),
                rs.getString("charge_status"),
                rs.getBoolean("without_next")),
        clinicId(),
        start,
        end);
  }

  private List<RegistryWorkQueueView.Item> failedDeliveries(Timestamp start, Timestamp end) {
    return jdbc.query(
        "select o.id as outbox_id, o.last_error, a.id, a.patient_id, patient.full_name as patient_name,"
            + " a.practitioner_id, practitioner.full_name as practitioner_name, a.starts_at,"
            + " a.service_name, a.status"
            + " from outbox_message o"
            + " join appointment a on a.id = o.aggregate_id"
            + " join patient on patient.id = a.patient_id"
            + " join practitioner on practitioner.id = a.practitioner_id"
            + " where o.clinic_id = ? and o.aggregate_type = 'APPOINTMENT' and o.status = 'FAILED'"
            + " and o.appointment_starts_at >= ? and o.appointment_starts_at < ?"
            + " order by a.starts_at, o.created_at",
        (rs, rowNumber) ->
            new RegistryWorkQueueView.Item(
                "delivery:" + rs.getString("outbox_id"),
                "DELIVERY",
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("patient_id")),
                rs.getString("patient_name"),
                UUID.fromString(rs.getString("practitioner_id")),
                rs.getString("practitioner_name"),
                rs.getTimestamp("starts_at").toInstant(),
                rs.getString("service_name"),
                rs.getString("status"),
                null,
                null,
                null,
                rs.getString("last_error") == null
                    ? "Сообщение не доставлено"
                    : "Сообщение не доставлено: " + rs.getString("last_error")),
        clinicId(),
        start,
        end);
  }

  private static RegistryWorkQueueView.Item item(
      AppointmentRow appointment,
      String kind,
      UUID chargeId,
      Long amountMinor,
      String reason) {
    return new RegistryWorkQueueView.Item(
        kind.toLowerCase() + ":" + appointment.id(),
        kind,
        appointment.id(),
        appointment.patientId(),
        appointment.patientName(),
        appointment.practitionerId(),
        appointment.practitionerName(),
        appointment.startsAt(),
        appointment.serviceName(),
        appointment.status(),
        chargeId,
        amountMinor,
        appointment.currency(),
        reason);
  }

  private record AppointmentRow(
      UUID id,
      UUID patientId,
      String patientName,
      UUID practitionerId,
      String practitionerName,
      Instant startsAt,
      String serviceName,
      String status,
      UUID chargeId,
      Long amountMinor,
      String currency,
      String chargeStatus,
      boolean withoutNext) {}
}
