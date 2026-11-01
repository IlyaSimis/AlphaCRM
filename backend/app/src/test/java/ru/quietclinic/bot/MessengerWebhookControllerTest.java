package ru.quietclinic.bot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.quietclinic.SecondClinic;
import ru.quietclinic.StaffSessions;

@SpringBootTest(
    properties = {
      "clinic.bot.api-token=" + MessengerWebhookControllerTest.LEGACY_TOKEN,
      "clinic.bot.clinic-id=00000000-0000-0000-0000-00000000c001"
    })
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MessengerWebhookControllerTest {
  static final String LEGACY_TOKEN = "legacy-bot-token-0123456789abcdef0123456789";
  private static final String TELEGRAM_SECRET = "telegram-webhook-secret";
  private static final String MAX_SECRET = "max-webhook-secret";
  private static final String PETROV = "00000000-0000-0000-0000-000000000201";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void setUp() {
    jdbc.update("delete from integration");
    staff = new StaffSessions(mvc);
  }

  @Test
  void webhookRequiresActiveIntegrationAndItsOwnSecret() throws Exception {
    var key = configure("TELEGRAM", TELEGRAM_SECRET);
    var body = "{\"update_id\":1}";
    mvc.perform(post(path("TELEGRAM", key)).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("MESSENGER_WEBHOOK_UNAUTHORIZED"));
    mvc.perform(
            post(path("TELEGRAM", key))
                .header(MessengerWebhookFilter.TELEGRAM_HEADER, LEGACY_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post(path("TELEGRAM", key))
                .header(MessengerWebhookFilter.TELEGRAM_HEADER, TELEGRAM_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accepted").value(true));
    mvc.perform(post("/api/v1/integrations/telegram/disable").with(staff.admin()))
        .andExpect(status().isOk());
    mvc.perform(
            post(path("TELEGRAM", key))
                .header(MessengerWebhookFilter.TELEGRAM_HEADER, TELEGRAM_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void telegramLinkAndReminderCallbackRunInsideClinic() throws Exception {
    var key = configure("TELEGRAM", TELEGRAM_SECRET);
    var linked = patientAndLink("TELEGRAM", staff.admin());
    var chatId = externalId();
    mvc.perform(
            post(path("TELEGRAM", key))
                .header(MessengerWebhookFilter.TELEGRAM_HEADER, TELEGRAM_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"message\":{\"text\":\"/start "
                        + linked.token()
                        + "\",\"chat\":{\"id\":"
                        + chatId
                        + "}}}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/patients/" + linked.patientId()).with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.telegramConnected").value(true));

    var appointment = book("TELEGRAM", chatId);
    mvc.perform(
            post(path("TELEGRAM", key))
                .header(MessengerWebhookFilter.TELEGRAM_HEADER, TELEGRAM_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"callback_query\":{\"data\":\"cf:"
                        + appointment
                        + ":yes\",\"message\":{\"chat\":{\"id\":"
                        + chatId
                        + "}}}}"))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/messenger/appointments/" + appointment)
                .param("channel", "TELEGRAM")
                .param("externalId", chatId)
                .with(legacyBot()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  @Test
  void maxLinkAndCallbacksUseMaxUserId() throws Exception {
    var key = configure("MAX", MAX_SECRET);
    var linked = patientAndLink("MAX", staff.admin());
    var userId = externalId();
    mvc.perform(
            post(path("MAX", key))
                .header(MessengerWebhookFilter.MAX_HEADER, MAX_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"update_type\":\"bot_started\",\"payload\":\""
                        + linked.token()
                        + "\",\"user\":{\"user_id\":"
                        + userId
                        + "}}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/patients/" + linked.patientId()).with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxConnected").value(true));

    var appointment = book("MAX", userId);
    for (var action : List.of("yes", "no", "no")) {
      mvc.perform(
              post(path("MAX", key))
                  .header(MessengerWebhookFilter.MAX_HEADER, MAX_SECRET)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"update_type\":\"message_callback\",\"callback\":{\"payload\":\"cf:"
                          + appointment
                          + ":"
                          + action
                          + "\",\"user\":{\"user_id\":"
                          + userId
                          + "}}}"))
          .andExpect(status().isOk());
    }
    mvc.perform(
            get("/api/v1/messenger/appointments/" + appointment)
                .param("channel", "MAX")
                .param("externalId", userId)
                .with(legacyBot()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void webhookCannotConsumeAnotherClinicsLink() throws Exception {
    var key = configure("TELEGRAM", TELEGRAM_SECRET);
    SecondClinic.ensure(jdbc);
    var secondAdmin = staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    var linked = patientAndLink("TELEGRAM", secondAdmin);
    mvc.perform(
            post(path("TELEGRAM", key))
                .header(MessengerWebhookFilter.TELEGRAM_HEADER, TELEGRAM_SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"message\":{\"text\":\"/start "
                        + linked.token()
                        + "\",\"chat\":{\"id\":"
                        + externalId()
                        + "}}}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MESSENGER_LINK_INVALID"));
  }

  private String configure(String type, String webhookSecret) throws Exception {
    var route = "/api/v1/integrations/" + type.toLowerCase(java.util.Locale.ROOT);
    var apiUrl = "MAX".equals(type) ? "https://platform-api2.max.ru" : "https://api.telegram.org";
    mvc.perform(
            put(route + "/settings")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"apiUrl\":\""
                        + apiUrl
                        + "\",\"publicUrl\":\"https://example.test/"
                        + type.toLowerCase(java.util.Locale.ROOT)
                        + "\",\"relayUrl\":\"\",\"reminderButtons\":true}"))
        .andExpect(status().isOk());
    mvc.perform(
            put(route + "/secret")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"botToken\":\"" + type.toLowerCase(java.util.Locale.ROOT) + "-token\"}"))
        .andExpect(status().isOk());
    // Секрет webhook обновляется отдельно: write-only bot token при этом сохраняется.
    var response =
        mvc.perform(
                put(route + "/secret")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"webhookSecret\":\"" + webhookSecret + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(post(route + "/activate").with(staff.admin())).andExpect(status().isOk());
    return JsonPath.read(response, "$.publicKey");
  }

  private LinkedPatient patientAndLink(String channel, RequestPostProcessor administrator)
      throws Exception {
    var suffix = externalId();
    var created =
        mvc.perform(
                post("/api/v1/patients")
                    .with(administrator)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"fullName\":\"Webhook "
                            + suffix
                            + "\",\"birthDate\":\"1990-05-17\",\"phone\":\"+7"
                            + suffix.substring(0, 10)
                            + "\",\"consentVersion\":\"v1\",\"consentBasis\":\"SIGNED_FORM\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String patientId = JsonPath.read(created, "$.id");
    var link =
        mvc.perform(
                post("/api/v1/patients/" + patientId + "/messenger/link")
                    .with(administrator)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"channel\":\"" + channel + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new LinkedPatient(patientId, JsonPath.read(link, "$.token"));
  }

  private String book(String channel, String externalId) throws Exception {
    var slotsResponse =
        mvc.perform(get("/api/v1/messenger/practitioners/" + PETROV + "/slots").with(legacyBot()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> slots = JsonPath.read(slotsResponse, "$[*].startsAt");
    var created =
        mvc.perform(
                post("/api/v1/messenger/appointments")
                    .with(legacyBot())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"channel\":\""
                            + channel
                            + "\",\"externalId\":\""
                            + externalId
                            + "\",\"lastName\":\"Webhook\",\"firstName\":\"Пациент\","
                            + "\"patronymic\":\"Тестович\",\"birthDate\":\"1990-05-17\","
                            + "\"phone\":\"+7 999 123-45-67\",\"practitionerId\":\""
                            + PETROV
                            + "\",\"startsAt\":\""
                            + slots.get(0)
                            + "\",\"consentVersion\":\"webhook-v1\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(created, "$.id");
  }

  private static RequestPostProcessor legacyBot() {
    return request -> {
      request.addHeader(BotTokenFilter.HEADER, LEGACY_TOKEN);
      return request;
    };
  }

  private static String path(String type, String key) {
    return "/api/v1/integrations/" + type.toLowerCase(java.util.Locale.ROOT) + "/events/" + key;
  }

  private static String externalId() {
    return String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L));
  }

  private record LinkedPatient(String patientId, String token) {}
}
