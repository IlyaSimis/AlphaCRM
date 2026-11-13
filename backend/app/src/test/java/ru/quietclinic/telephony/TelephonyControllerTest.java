package ru.quietclinic.telephony;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.quietclinic.StaffSessions;

@SpringBootTest(
    properties = {
      "clinic.telephony.mango.api-key=test-mango-key",
      "clinic.telephony.mango.api-salt=test-mango-salt"
    })
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TelephonyControllerTest {
  private static final String ANNA = "00000000-0000-0000-0000-000000000101";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean MangoGateway mango;
  StaffSessions staff;

  @BeforeEach
  void setUp() {
    jdbc.update("delete from integration");
    staff = new StaffSessions(mvc);
    when(mango.configured()).thenReturn(true);
    when(mango.defaultExtension()).thenReturn("101");
  }

  @Test
  void startsSignedCallbackFromPatientCardAndStoresHistory() throws Exception {
    mvc.perform(
            post("/api/v1/telephony/calls")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":\"" + ANNA + "\",\"extension\":\"102\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("REQUESTED"))
        .andExpect(jsonPath("$.extension").value("102"));
    verify(mango)
        .callback(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq("102"),
            ArgumentMatchers.eq("79162451872"));
    mvc.perform(get("/api/v1/telephony/patients/" + ANNA + "/calls").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].direction").value(hasItem("OUTBOUND")));
  }

  @Test
  void acceptsSignedMangoEventOnlyOnce() throws Exception {
    String body =
        """
        {"entry_id":"entry-test-1","call_id":"call-test-1","timestamp":1793200000,"seq":2,
         "call_state":"Disconnected","from":{"number":"+7 916 245-18-72"},
         "to":{"extension":"101"}}
        """;
    for (int attempt = 0; attempt < 2; attempt++) {
      mvc.perform(
              post("/api/v1/integrations/mango/events/call")
                  .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                  .param("vpbx_api_key", "test-mango-key")
                  .param("json", body)
                  .param("sign", MangoSignature.sign("test-mango-key", body, "test-mango-salt")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accepted").value(true));
    }
    Integer events =
        jdbc.queryForObject(
            "select count(*) from telephony_event where entry_id = 'entry-test-1'", Integer.class);
    Assertions.assertThat(events).isEqualTo(1);
    mvc.perform(get("/api/v1/telephony/patients/" + ANNA + "/calls").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].status").value(hasItem("Disconnected")));
  }

  @Test
  void rejectsUnsignedEventAndForbiddenCall() throws Exception {
    mvc.perform(
            post("/api/v1/integrations/mango/events/call")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("vpbx_api_key", "test-mango-key")
                .param("json", "{}")
                .param("sign", "wrong"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/telephony/calls")
                .with(staff.role("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":\"" + ANNA + "\",\"extension\":\"102\"}"))
        .andExpect(status().isForbidden());
  }
}
