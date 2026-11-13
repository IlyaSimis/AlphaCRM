package ru.quietclinic.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.quietclinic.StaffSessions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MessengerWebhookRegistrationControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean MessengerProviderHttp http;
  StaffSessions staff;

  @BeforeEach
  void setUp() {
    jdbc.update("delete from integration");
    staff = new StaffSessions(mvc);
  }

  @Test
  void registersTelegramAndReportsDeliveryError() throws Exception {
    var key = configure("TELEGRAM");
    var callback = callback("telegram", key);
    when(http.postJson(any(), anyString(), anyMap()))
        .thenAnswer(
            invocation -> {
              var uri = invocation.getArgument(0).toString();
              if (uri.endsWith("/setWebhook")) return response("{\"ok\":true}");
              return response(
                  "{\"ok\":true,\"result\":{\"url\":\""
                      + callback
                      + "\",\"pending_update_count\":3}}");
            });

    mvc.perform(post("/api/v1/integrations/telegram/webhook/connect").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("CONNECTED"))
        .andExpect(jsonPath("$.url").value(callback))
        .andExpect(jsonPath("$.pendingUpdates").value(3));
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(http)
        .postJson(
            argThat(uri -> uri.toString().endsWith("/bottelegram-token/setWebhook")),
            body.capture(),
            anyMap());
    assertThat(body.getValue())
        .contains("telegram-webhook-secret")
        .contains("callback_query")
        .doesNotContain("telegram-token");

    doReturn(
            response(
                "{\"ok\":true,\"result\":{\"url\":\""
                    + callback
                    + "\",\"last_error_message\":\"Connection refused\"}}"))
        .when(http)
        .postJson(
            argThat(uri -> uri.toString().endsWith("/getWebhookInfo")), anyString(), anyMap());
    mvc.perform(get("/api/v1/integrations/telegram/webhook/status").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("ERROR"))
        .andExpect(jsonPath("$.detail").value("Connection refused"));
  }

  @Test
  void registersMaxWithAuthorizationAndChecksSubscription() throws Exception {
    var key = configure("MAX");
    var callback = callback("max", key);
    when(http.postJson(any(), anyString(), anyMap())).thenReturn(response("{\"success\":true}"));
    when(http.get(any(), anyMap()))
        .thenReturn(response("{\"subscriptions\":[{\"url\":\"" + callback + "\"}]}"));

    mvc.perform(post("/api/v1/integrations/max/webhook/connect").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("CONNECTED"));
    ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
    verify(http)
        .postJson(
            argThat(uri -> uri.toString().equals("https://platform-api2.max.ru/subscriptions")),
            argThat(
                body ->
                    body.contains("max-webhook-secret")
                        && body.contains("message_callback")
                        && !body.contains("max-token")),
            headers.capture());
    assertThat(headers.getValue()).containsEntry("Authorization", "max-token");

    when(http.get(any(), anyMap())).thenReturn(response("{\"subscriptions\":[]}"));
    mvc.perform(get("/api/v1/integrations/max/webhook/status").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("NOT_CONFIGURED"));
  }

  @Test
  void validatesPublicUrlRelayAndPermissions() throws Exception {
    configure("TELEGRAM");
    mvc.perform(
            put("/api/v1/integrations/telegram/settings")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"apiUrl\":\"https://api.telegram.org\",\"publicUrl\":\"\","
                        + "\"relayUrl\":\"https://relay.example.test\",\"webhookBaseUrl\":\"https://crm.example.test\"}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/integrations/telegram/webhook/connect").with(staff.admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INTEGRATION_RELAY_WEBHOOK_EXTERNAL"));
    mvc.perform(post("/api/v1/integrations/max/webhook/connect").with(staff.role("CASHIER")))
        .andExpect(status().isForbidden());
    verify(http, never()).postJson(any(), anyString(), anyMap());
  }

  private String configure(String type) throws Exception {
    var route = "/api/v1/integrations/" + type.toLowerCase(java.util.Locale.ROOT);
    var apiUrl = "MAX".equals(type) ? "https://platform-api2.max.ru" : "https://api.telegram.org";
    mvc.perform(
            put(route + "/settings")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"apiUrl\":\""
                        + apiUrl
                        + "\",\"publicUrl\":\"\",\"relayUrl\":\"\","
                        + "\"webhookBaseUrl\":\"https://crm.example.test\",\"reminderButtons\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.settings.webhookBaseUrl").value("https://crm.example.test"));
    var response =
        mvc.perform(
                put(route + "/secret")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"botToken\":\""
                            + type.toLowerCase(java.util.Locale.ROOT)
                            + "-token\",\"webhookSecret\":\""
                            + type.toLowerCase(java.util.Locale.ROOT)
                            + "-webhook-secret\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(post(route + "/activate").with(staff.admin())).andExpect(status().isOk());
    return JsonPath.read(response, "$.publicKey");
  }

  private static String callback(String type, String key) {
    return "https://crm.example.test/api/v1/integrations/" + type + "/events/" + key;
  }

  private static MessengerProviderHttp.ProviderResponse response(String body) {
    return new MessengerProviderHttp.ProviderResponse(200, body);
  }
}
