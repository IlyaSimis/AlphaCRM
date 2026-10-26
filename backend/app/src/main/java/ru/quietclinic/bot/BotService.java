package ru.quietclinic.bot;

import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.notFound;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.quietclinic.patients.TelegramPatient;
import ru.quietclinic.patients.TelegramPatientRequest;
import ru.quietclinic.patients.TelegramPatients;
import ru.quietclinic.platform.ClinicTime;
import ru.quietclinic.registry.OperatorRequests;
import ru.quietclinic.scheduling.BookablePractitioner;
import ru.quietclinic.scheduling.OnlineAppointment;
import ru.quietclinic.scheduling.OnlineBooking;

/**
 * API Telegram-бота поверх публичных сервисов расписания, пациентов и регистратуры. Запись, которую
 * бот подтверждает или отменяет, должна принадлежать пациенту с chat id того, кто нажал кнопку:
 * callback_data в Telegram можно подделать, поэтому чужая запись выглядит как несуществующая.
 */
@Service
class BotService {
  private final OnlineBooking booking;
  private final TelegramPatients patients;
  private final OperatorRequests operator;
  private final ClinicTime time;
  private final JdbcTemplate jdbc;

  BotService(
      OnlineBooking booking,
      TelegramPatients patients,
      OperatorRequests operator,
      ClinicTime time,
      JdbcTemplate jdbc) {
    this.booking = booking;
    this.patients = patients;
    this.operator = operator;
    this.time = time;
    this.jdbc = jdbc;
  }

  /** Клиника отвечает и при ненастроенной услуге онлайн-записи: тогда serviceName — null. */
  BotClinicView clinic() {
    var name = jdbc.queryForObject("select name from clinic where id = ?", String.class, clinicId());
    String service;
    try {
      service = booking.serviceName();
    } catch (RuntimeException ex) {
      service = null;
    }
    return new BotClinicView(name, time.zone().getId(), service);
  }

  List<BotPractitionerView> practitioners() {
    return booking.practitioners().stream().map(BotService::view).toList();
  }

  BotPractitionerView practitioner(UUID id) {
    return booking
        .practitioner(id)
        .map(BotService::view)
        .orElseThrow(() -> notFound("PRACTITIONER_NOT_FOUND", "Practitioner not found"));
  }

  List<BotSlotView> slots(UUID practitionerId) {
    return booking.freeSlots(practitionerId).stream()
        .map(s -> new BotSlotView(local(s.startsAt()), local(s.endsAt())))
        .toList();
  }

  BotPatientView patient(String chatId) {
    return patients
        .findByChatId(chatId)
        .map(BotService::view)
        .orElseThrow(() -> notFound("PATIENT_NOT_FOUND", "Patient not found"));
  }

  BotAppointmentView book(BotBookingRequest request) {
    var patientId =
        patients.resolveForBooking(
            new TelegramPatientRequest(
                request.telegramChatId(),
                request.fullName(),
                request.birthDate(),
                request.phone().trim(),
                request.consentVersion()));
    return view(booking.book(patientId, request.practitionerId(), request.startsAt().toInstant()));
  }

  BotAppointmentView appointment(UUID id, String chatId) {
    return view(owned(id, chatId));
  }

  BotAppointmentView confirm(UUID id, String chatId) {
    owned(id, chatId);
    return view(booking.confirm(id));
  }

  BotAppointmentView cancel(UUID id, String chatId) {
    owned(id, chatId);
    return view(booking.cancel(id));
  }

  BotTicketView operatorRequest(BotOperatorRequest request) {
    var patientId =
        patients.findByChatId(request.telegramChatId()).map(TelegramPatient::id).orElse(null);
    return new BotTicketView(operator.create(request.title(), patientId), patientId);
  }

  private OnlineAppointment owned(UUID id, String chatId) {
    return booking
        .appointment(id)
        .filter(a -> patients.isLinked(a.patientId(), chatId))
        .orElseThrow(() -> notFound("APPOINTMENT_NOT_FOUND", "Appointment not found"));
  }

  private BotAppointmentView view(OnlineAppointment a) {
    var practitioner = booking.practitioner(a.practitionerId()).orElse(null);
    var patient = patients.find(a.patientId()).map(BotService::view).orElse(null);
    return new BotAppointmentView(
        a.id(),
        a.practitionerId(),
        practitioner == null ? null : practitioner.fullName(),
        local(a.startsAt()),
        local(a.endsAt()),
        a.serviceName(),
        a.status(),
        patient);
  }

  private OffsetDateTime local(Instant instant) {
    return instant.atZone(time.zone()).toOffsetDateTime();
  }

  private static BotPractitionerView view(BookablePractitioner p) {
    return new BotPractitionerView(p.id(), p.fullName(), p.specialty(), p.room());
  }

  private static BotPatientView view(TelegramPatient p) {
    return new BotPatientView(p.id(), p.fullName(), p.birthDate(), p.phone());
  }
}
