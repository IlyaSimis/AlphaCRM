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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.quietclinic.StaffSessions;

/** MAX использует те же правила пациента и записи, что Telegram; привязка одноразовая. */
@SpringBootTest(
    properties = {
      "clinic.bot.api-token=" + MessengerControllerTest.TOKEN,
      "clinic.bot.clinic-id=00000000-0000-0000-0000-00000000c001"
    })
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MessengerControllerTest {
  static final String TOKEN = "test-messenger-token-0123456789abcdef0123456789";
  static final String PETROV = "00000000-0000-0000-0000-000000000201";
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

  private static String externalId() {
    return String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000L, 9_000_000_000L));
  }

  @Test
  void maxLinkIsSingleUseAndPreferredChannelCanBeAutomatic() throws Exception {
    var created =
        mvc.perform(
                post("/api/v1/patients")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                {"fullName":"Максова Анна Сергеевна","birthDate":"1990-05-17",
                 "phone":"+7 999 765-43-21","consentVersion":"v1","consentBasis":"SIGNED_FORM"}
                """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String patient = JsonPath.read(created, "$.id");
    var link =
        mvc.perform(
                post("/api/v1/patients/" + patient + "/messenger/link")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"channel\":\"MAX\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = JsonPath.read(link, "$.token");
    String user = externalId();
    var consume =
        "{\"channel\":\"MAX\",\"externalId\":\"" + user + "\",\"token\":\"" + token + "\"}";
    mvc.perform(
            post("/api/v1/messenger/link/consume")
                .with(bot())
                .contentType(MediaType.APPLICATION_JSON)
                .content(consume))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Максова Анна Сергеевна"));
    mvc.perform(
            post("/api/v1/messenger/link/consume")
                .with(bot())
                .contentType(MediaType.APPLICATION_JSON)
                .content(consume))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MESSENGER_LINK_INVALID"));
    mvc.perform(get("/api/v1/messenger/patients/MAX/" + user).with(bot()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(patient));
    mvc.perform(get("/api/v1/patients/" + patient).with(staff.admin()))
        .andExpect(jsonPath("$.maxConnected").value(true))
        .andExpect(jsonPath("$.preferredMessenger").value("MAX"))
        .andExpect(jsonPath("$.contacts[?(@.type == 'MAX')]").isEmpty());
    mvc.perform(
            put("/api/v1/patients/" + patient + "/messenger/preference")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"TELEGRAM\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MESSENGER_NOT_CONNECTED"));
    mvc.perform(
            put("/api/v1/patients/" + patient + "/messenger/preference")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preferredMessenger").doesNotExist());
    mvc.perform(
            post("/api/v1/patients/" + patient + "/messenger/MAX/disconnect").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.maxConnected").value(false));
  }

  @Test
  void maxBookingAndActionsRequireTheLinkedExternalId() throws Exception {
    var body =
        mvc.perform(get("/api/v1/messenger/practitioners/" + PETROV + "/slots").with(bot()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> slots = JsonPath.read(body, "$[*].startsAt");
    String user = externalId();
    var booking =
        """
        {"channel":"MAX","externalId":"%s","lastName":"Максова","firstName":"Анна",
         "patronymic":"Сергеевна","birthDate":"1990-05-17","phone":"+7 999 123-45-67",
         "practitionerId":"%s","startsAt":"%s","consentVersion":"max-bot-v1"}
        """
            .formatted(user, PETROV, slots.get(1));
    var created =
        mvc.perform(
                post("/api/v1/messenger/appointments")
                    .with(bot())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(booking))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("BOOKED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String appointment = JsonPath.read(created, "$.id");
    mvc.perform(
            get("/api/v1/messenger/appointments")
                .with(bot())
                .param("channel", "MAX")
                .param("externalId", user))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(appointment));
    mvc.perform(
            post("/api/v1/messenger/appointments/" + appointment + "/confirm")
                .with(bot())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"MAX\",\"externalId\":\"" + externalId() + "\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/v1/messenger/appointments/" + appointment + "/confirm")
                .with(bot())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"MAX\",\"externalId\":\"" + user + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  @Test
  void maxBookingLinksExistingPatientWithoutCreatingDuplicate() throws Exception {
    var createdPatient =
        mvc.perform(
                post("/api/v1/patients")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                {"fullName":"Существующая Вера Ивановна","birthDate":"1988-04-12",
                 "phone":"+7 999 555-44-33","consentVersion":"v1","consentBasis":"SIGNED_FORM"}
                """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String patient = JsonPath.read(createdPatient, "$.id");
    var body =
        mvc.perform(get("/api/v1/messenger/practitioners/" + PETROV + "/slots").with(bot()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> slots = JsonPath.read(body, "$[*].startsAt");
    String user = externalId();
    var booking =
        """
        {"channel":"MAX","externalId":"%s","lastName":"Существующая","firstName":"Вера",
         "patronymic":"Ивановна","birthDate":"1988-04-12","phone":"+7 999 555-44-33",
         "practitionerId":"%s","startsAt":"%s","consentVersion":"max-bot-v1"}
        """
            .formatted(user, PETROV, slots.get(2));
    mvc.perform(
            post("/api/v1/messenger/appointments")
                .with(bot())
                .contentType(MediaType.APPLICATION_JSON)
                .content(booking))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.patient.id").value(patient));
    mvc.perform(get("/api/v1/messenger/patients/MAX/" + user).with(bot()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(patient));
  }
}
