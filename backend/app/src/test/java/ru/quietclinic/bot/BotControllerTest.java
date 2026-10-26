package ru.quietclinic.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.quietclinic.StaffSessions;

/**
 * API Telegram-бота: вход по токену, врачи и окна, запись нового пациента, подтверждение и отмена
 * только своим chat id, обращение к оператору как задача регистратуры.
 */
@SpringBootTest(
    properties = {
      "clinic.bot.api-token=" + BotControllerTest.TOKEN,
      "clinic.bot.clinic-id=00000000-0000-0000-0000-00000000c001"
    })
@AutoConfigureMockMvc
@ActiveProfiles("local")
class BotControllerTest {
  static final String TOKEN = "test-bot-token-0123456789abcdef0123456789";
  static final String PETROV = "00000000-0000-0000-0000-000000000201";
  /** «Сейчас» тестов (clinic.fixed-time) плюс запас min-lead в два часа. */
  static final Instant EARLIEST = Instant.parse("2026-10-19T02:00:00Z");

  @Autowired MockMvc mvc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  private static RequestPostProcessor bot() {
    return request -> {
      request.addHeader(BotTokenFilter.HEADER, TOKEN);
      return request;
    };
  }

  private static String chat() {
    return String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000L, 9_000_000_000L));
  }

  private List<String> slots(String practitionerId) throws Exception {
    var body =
        mvc.perform(get("/api/v1/bot/practitioners/" + practitionerId + "/slots").with(bot()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$[*].startsAt");
  }

  private ResultActions book(String chatId, String lastName, String startsAt) throws Exception {
    var body =
        """
        {"telegramChatId":"%s","lastName":"%s","firstName":"Анна","patronymic":"Сергеевна",
         "birthDate":"1990-05-17","phone":"+7 999 123-45-67","practitionerId":"%s",
         "startsAt":"%s","consentVersion":"telegram-bot-v1"}
        """
            .formatted(chatId, lastName, PETROV, startsAt);
    return mvc.perform(
        post("/api/v1/bot/appointments")
            .with(bot())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private ResultActions act(String appointmentId, String action, String chatId) throws Exception {
    return mvc.perform(
        post("/api/v1/bot/appointments/" + appointmentId + "/" + action)
            .with(bot())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"telegramChatId\":\"" + chatId + "\"}"));
  }

  private static String id(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
  }

  @Test
  void botApiNeedsItsOwnToken() throws Exception {
    mvc.perform(get("/api/v1/bot/practitioners"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("BOT_TOKEN_INVALID"));
    mvc.perform(get("/api/v1/bot/practitioners").header(BotTokenFilter.HEADER, "wrong"))
        .andExpect(status().isUnauthorized());
    // Сессия сотрудника на API бота не действует.
    mvc.perform(get("/api/v1/bot/practitioners").with(staff.admin()))
        .andExpect(status().isUnauthorized());
    // А токен бота не открывает API сотрудников.
    mvc.perform(get("/api/v1/patients").with(bot())).andExpect(status().isUnauthorized());
  }

  @Test
  void clinicAndPractitionersWithShifts() throws Exception {
    mvc.perform(get("/api/v1/bot/clinic").with(bot()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").isNotEmpty())
        .andExpect(jsonPath("$.timeZone").value("UTC"))
        .andExpect(jsonPath("$.serviceName").value("Первичный приём"));
    mvc.perform(get("/api/v1/bot/practitioners").with(bot()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem(PETROV)));
    mvc.perform(get("/api/v1/bot/practitioners/00000000-0000-0000-0000-000000000999").with(bot()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PRACTITIONER_NOT_FOUND"));
  }

  @Test
  void slotsLieInsideShiftsAndAfterTheLead() throws Exception {
    var starts = slots(PETROV);
    assertThat(starts).isNotEmpty();
    for (var raw : starts) {
      var start = OffsetDateTime.parse(raw);
      // Петров работает по вторникам и четвергам 09:00–18:00; первичный приём — 50 минут.
      assertThat(start.getDayOfWeek()).isIn(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
      assertThat(start.toLocalTime()).isBetween(LocalTime.of(9, 0), LocalTime.of(17, 10));
      assertThat(start.toInstant()).isAfterOrEqualTo(EARLIEST);
    }
  }

  @Test
  void booksAndOnlyTheOwnChatConfirmsOrCancels() throws Exception {
    var chat = chat();
    var slot = slots(PETROV).get(2);
    var booked =
        book(chat, "Ботова", slot)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("BOOKED"))
            .andExpect(jsonPath("$.serviceName").value("Первичный приём"))
            .andExpect(jsonPath("$.practitionerName").value("Александр Петров"))
            .andExpect(jsonPath("$.patient.fullName").value("Ботова Анна Сергеевна"))
            .andExpect(jsonPath("$.patient.phone").value("+7 999 123-45-67"));
    var appointment = id(booked);
    assertThat(slots(PETROV)).doesNotContain(slot);
    book(chat(), "Другая", slot)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SLOT_ALREADY_BOOKED"));

    mvc.perform(get("/api/v1/bot/patients/by-telegram/" + chat).with(bot()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Ботова Анна Сергеевна"))
        .andExpect(jsonPath("$.birthDate").value("1990-05-17"));

    // Чужой chat id — запись как будто не существует: callback_data можно подделать.
    act(appointment, "confirm", chat())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"));
    mvc.perform(
            get("/api/v1/bot/appointments/" + appointment)
                .param("telegramChatId", chat())
                .with(bot()))
        .andExpect(status().isNotFound());

    act(appointment, "confirm", chat).andExpect(jsonPath("$.status").value("CONFIRMED"));
    act(appointment, "confirm", chat).andExpect(jsonPath("$.status").value("CONFIRMED"));
    act(appointment, "cancel", chat).andExpect(jsonPath("$.status").value("CANCELLED"));
    act(appointment, "cancel", chat)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_CANCELLED"));
    assertThat(slots(PETROV)).contains(slot);
  }

  @Test
  void samePersonKeepsOneCardLinkedToTelegram() throws Exception {
    var chat = chat();
    var all = slots(PETROV);
    var first =
        JsonPath.read(
            book(chat, "Повторова", all.get(all.size() - 1))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.patient.id");
    var second =
        JsonPath.read(
            book(chat, "повторова", all.get(all.size() - 2))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.patient.id");
    assertThat(second).isEqualTo(first);
    mvc.perform(get("/api/v1/patients/" + first).with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.telegramConnected").value(true));
  }

  @Test
  void onlineBookingSkipsTheConfirmationMessage() throws Exception {
    var all = slots(PETROV);
    var appointment = id(book(chat(), "Очередева", all.get(all.size() / 2)));
    var body =
        mvc.perform(get("/api/v1/outbox").with(staff.admin()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Map<String, Object>> confirmations =
        JsonPath.read(
            body,
            "$[?(@.aggregateId == '"
                + appointment
                + "' && @.template == 'appointment-confirmed')]");
    assertThat(confirmations).hasSize(1);
    assertThat(confirmations.get(0)).containsEntry("status", "SKIPPED");
    assertThat(confirmations.get(0)).containsEntry("reason", "SHOWN_IN_BOT");
  }

  @Test
  void operatorRequestBecomesRegistryTask() throws Exception {
    var created =
        mvc.perform(
                post("/api/v1/bot/operator-requests")
                    .with(bot())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"telegramChatId\":\""
                            + chat()
                            + "\",\"title\":\"Telegram: пациент просит оператора\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.patientId").doesNotExist());
    var task = id(created);
    mvc.perform(get("/api/v1/registry/tasks").param("dueTo", "2026-12-31").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem(task)))
        .andExpect(jsonPath("$[*].title", hasItem("Telegram: пациент просит оператора")));
  }

  @Test
  void bookingNeedsPatientData() throws Exception {
    mvc.perform(
            post("/api/v1/bot/appointments")
                .with(bot())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"telegramChatId":"123","lastName":"Без","firstName":"Телефона",
                     "birthDate":"1990-05-17","practitionerId":"%s",
                     "startsAt":"2026-10-20T11:00:00Z","consentVersion":"v1"}
                    """
                        .formatted(PETROV)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }
}
