package ru.quietclinic.assistant;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.quietclinic.StaffSessions;
import ru.quietclinic.assistant.AssistantGateway.GatewayResult;

/** ИИ возвращает только разрешённый id, без тарифа и месячного учёта обращений. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AssistantControllerTest {
  private static final String REQUEST =
      """
      {"query":"что у меня на сегодня","currentPage":"today","destinations":[
        {"id":"workspace-today","title":"Задачи на сегодня","description":"Личные задачи","keywords":["сегодня"]}
      ]}
      """;

  @Autowired MockMvc mvc;
  @MockitoBean AssistantGateway gateway;
  StaffSessions staff;

  @BeforeEach
  void setUp() {
    staff = new StaffSessions(mvc);
    when(gateway.enabled()).thenReturn(true);
    when(gateway.model()).thenReturn("test-model");
  }

  @Test
  void statusIsAvailableToEveryStaffRoleWithoutUsageQuota() throws Exception {
    mvc.perform(get("/api/v1/assistant/status").with(staff.role("DOCTOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.model").value("test-model"))
        .andExpect(jsonPath("$.usage").doesNotExist());
  }

  @Test
  void navigationRequiresSession() throws Exception {
    mvc.perform(
            post("/api/v1/assistant/navigate")
                .with(staff.anonymous())
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void acceptedDestinationIsReturnedWithoutUsageAccounting() throws Exception {
    when(gateway.navigate(anyString(), anyString(), anyList()))
        .thenReturn(new GatewayResult("workspace-today", 120, 30));

    mvc.perform(
            post("/api/v1/assistant/navigate")
                .with(staff.role("SUPPORT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.destinationId").value("workspace-today"))
        .andExpect(jsonPath("$.source").value("AI"))
        .andExpect(jsonPath("$.usage").doesNotExist());

    verify(gateway).navigate(eq("что у меня на сегодня"), eq("today"), anyList());
  }

  @Test
  void destinationOutsideCatalogIsRejected() throws Exception {
    when(gateway.navigate(anyString(), anyString(), anyList()))
        .thenReturn(new GatewayResult("cashier", 100, 20));

    mvc.perform(
            post("/api/v1/assistant/navigate")
                .with(staff.role("DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ASSISTANT_UNKNOWN_DESTINATION"));
  }
}
