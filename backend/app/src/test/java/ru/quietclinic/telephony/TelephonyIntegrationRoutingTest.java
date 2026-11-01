package ru.quietclinic.telephony;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.quietclinic.StaffSessions;
import ru.quietclinic.integrations.MangoClient;
import ru.quietclinic.integrations.MangoIntegrationClientFactory;

@SpringBootTest(
    properties = {
      "clinic.telephony.mango.api-key=legacy-key",
      "clinic.telephony.mango.api-salt=legacy-salt"
    })
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TelephonyIntegrationRoutingTest {
  private static final UUID MAIN_CLINIC = UUID.fromString("00000000-0000-0000-0000-00000000c001");
  private static final String ANNA = "00000000-0000-0000-0000-000000000101";

  @Autowired MockMvc mvc;
  @MockitoBean MangoIntegrationClientFactory integrations;
  @MockitoBean MangoGateway legacy;
  @MockitoBean MangoClient scoped;
  StaffSessions staff;

  @BeforeEach
  void setUp() {
    staff = new StaffSessions(mvc);
    when(integrations.forClinic(MAIN_CLINIC)).thenReturn(Optional.of(scoped));
    when(scoped.configured()).thenReturn(true);
    when(scoped.defaultExtension()).thenReturn("202");
  }

  @Test
  void usesScopedConfigurationInsteadOfLegacy() throws Exception {
    mvc.perform(get("/api/v1/telephony/status").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(true))
        .andExpect(jsonPath("$.defaultExtension").value("202"));
    mvc.perform(
            post("/api/v1/telephony/calls")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":\"" + ANNA + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.extension").value("202"));
    verify(scoped)
        .callback(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.eq("202"),
            ArgumentMatchers.eq("79162451872"));
    verify(legacy, never())
        .callback(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString());
  }

  @Test
  void doesNotFallBackWhenRegistryIntegrationIsNotConfigured() throws Exception {
    when(scoped.configured()).thenReturn(false);
    mvc.perform(
            post("/api/v1/telephony/calls")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":\"" + ANNA + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("TELEPHONY_NOT_CONFIGURED"));
    verify(legacy, never())
        .callback(
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString(),
            ArgumentMatchers.anyString());
  }
}
