package ru.quietclinic.telephony;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.quietclinic.SecondClinic;
import ru.quietclinic.StaffSessions;

@SpringBootTest(
    properties = {
      "clinic.telephony.mango.api-key=legacy-key",
      "clinic.telephony.mango.api-salt=legacy-salt"
    })
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MangoWebhookRoutingTest {
  private static final UUID MAIN_CLINIC = UUID.fromString("00000000-0000-0000-0000-00000000c001");
  private static final UUID MAIN_PATIENT = UUID.fromString("00000000-0000-0000-0000-000000000101");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void setUp() {
    jdbc.update("delete from integration");
    SecondClinic.ensure(jdbc);
    staff = new StaffSessions(mvc);
  }

  @Test
  void routesCallsAndRecordingsToTheClinicSelectedByPublicKey() throws Exception {
    String mainKey = configure(staff.admin(), "clinic-a-key", "clinic-a-salt");
    StaffSessions.Login secondAdmin =
        staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    String secondKey = configure(secondAdmin, "clinic-b-key", "clinic-b-salt");

    scopedEvent(
            mainKey, "call", callBody("webhook-main", "call-main"), "clinic-a-key", "clinic-a-salt")
        .andExpect(status().isOk());
    scopedEvent(
            secondKey,
            "call",
            callBody("webhook-second", "call-second"),
            "clinic-b-key",
            "clinic-b-salt")
        .andExpect(status().isOk());

    assertThat(clinicForEvent("webhook-main")).isEqualTo(MAIN_CLINIC);
    assertThat(clinicForEvent("webhook-second")).isEqualTo(SecondClinic.ID);
    assertThat(patientForCall(MAIN_CLINIC, "call-main")).isEqualTo(MAIN_PATIENT);

    scopedEvent(
            mainKey,
            "recording",
            recordingBody("recording-main", "call-main", "recording-id-main"),
            "clinic-a-key",
            "clinic-a-salt")
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "select recording_id from telephony_call where clinic_id = ? and call_id = ?",
                String.class,
                MAIN_CLINIC,
                "call-main"))
        .isEqualTo("recording-id-main");

    scopedEvent(
            mainKey,
            "call",
            callBody("webhook-wrong-secret", "call-wrong-secret"),
            "clinic-b-key",
            "clinic-b-salt")
        .andExpect(status().isUnauthorized());
    assertThat(eventCount("webhook-wrong-secret")).isZero();
  }

  @Test
  void neverFallsBackToLegacyForUnknownInvalidOrDisabledScopedWebhook() throws Exception {
    String publicKey = configure(staff.admin(), "clinic-a-key", "clinic-a-salt");
    mvc.perform(post("/api/v1/integrations/mango/disable").with(staff.admin()))
        .andExpect(status().isOk());

    for (String pathKey : new String[] {publicKey, UUID.randomUUID().toString(), "not-a-uuid"}) {
      String entryId = "webhook-no-fallback-" + pathKey;
      scopedEvent(
              pathKey,
              "call",
              callBody(entryId, "call-no-fallback-" + pathKey),
              "legacy-key",
              "legacy-salt")
          .andExpect(status().isUnauthorized());
      assertThat(eventCount(entryId)).isZero();
    }
  }

  @Test
  void verifiesScopedWebhookWithTheLatestSecretVersion() throws Exception {
    String publicKey = configure(staff.admin(), "before-rotation-key", "before-rotation-salt");
    mvc.perform(
            put("/api/v1/integrations/mango/secret")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"after-rotation-key\",\"apiSalt\":\"after-rotation-salt\"}"))
        .andExpect(status().isOk());

    scopedEvent(
            publicKey,
            "call",
            callBody("webhook-old-version", "call-old-version"),
            "before-rotation-key",
            "before-rotation-salt")
        .andExpect(status().isUnauthorized());
    scopedEvent(
            publicKey,
            "call",
            callBody("webhook-new-version", "call-new-version"),
            "after-rotation-key",
            "after-rotation-salt")
        .andExpect(status().isOk());
    assertThat(eventCount("webhook-old-version")).isZero();
    assertThat(eventCount("webhook-new-version")).isEqualTo(1);
  }

  private String configure(RequestPostProcessor session, String apiKey, String apiSalt)
      throws Exception {
    mvc.perform(
            put("/api/v1/integrations/mango/settings")
                .with(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"apiUrl\":\"https://app.mango-office.ru\",\"defaultExtension\":\"101\",\"lineNumber\":\"\"}"))
        .andExpect(status().isOk());
    String response =
        mvc.perform(
                put("/api/v1/integrations/mango/secret")
                    .with(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"apiKey\":\"" + apiKey + "\",\"apiSalt\":\"" + apiSalt + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(post("/api/v1/integrations/mango/activate").with(session))
        .andExpect(status().isOk());
    return JsonPath.read(response, "$.publicKey");
  }

  private ResultActions scopedEvent(
      String publicKey, String event, String body, String apiKey, String apiSalt) throws Exception {
    return mvc.perform(
        post("/api/v1/integrations/mango/events/{publicKey}/{event}", publicKey, event)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("vpbx_api_key", apiKey)
            .param("json", body)
            .param("sign", MangoSignature.sign(apiKey, body, apiSalt)));
  }

  private UUID clinicForEvent(String entryId) {
    return jdbc.queryForObject(
        "select clinic_id from telephony_event where entry_id = ?", UUID.class, entryId);
  }

  private UUID patientForCall(UUID clinicId, String callId) {
    return jdbc.queryForObject(
        "select patient_id from telephony_call where clinic_id = ? and call_id = ?",
        UUID.class,
        clinicId,
        callId);
  }

  private int eventCount(String entryId) {
    Integer count =
        jdbc.queryForObject(
            "select count(*) from telephony_event where entry_id = ?", Integer.class, entryId);
    return count == null ? 0 : count;
  }

  private static String callBody(String entryId, String callId) {
    return """
        {"entry_id":"%s","call_id":"%s","timestamp":1793200000,"seq":2,
         "call_state":"Disconnected","from":{"number":"+7 916 245-18-72"},
         "to":{"extension":"101"}}
        """
        .formatted(entryId, callId);
  }

  private static String recordingBody(String entryId, String callId, String recordingId) {
    return """
        {"entry_id":"%s","call_id":"%s","recording_id":"%s","seq":3}
        """
        .formatted(entryId, callId, recordingId);
  }
}
