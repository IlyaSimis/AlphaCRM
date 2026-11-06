package ru.quietclinic.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.StaffSessions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class YandexMetricaControllerTest {
  @Autowired MockMvc mvc;
  StaffSessions staff;
  HttpServer server;
  String apiUrl;
  AtomicReference<String> authorization = new AtomicReference<>();
  AtomicReference<String> uploadBody = new AtomicReference<>();
  java.util.concurrent.atomic.AtomicInteger uploadCalls =
      new java.util.concurrent.atomic.AtomicInteger();

  @BeforeEach
  void setUp() throws IOException {
    staff = new StaffSessions(mvc);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/management/v1/counter/123456/offline_conversions/upload",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          uploadCalls.incrementAndGet();
          uploadBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          respond(exchange, "{\"uploading\":{\"id\":\"upload-42\"}}");
        });
    server.createContext(
        "/management/v1/counter/123456",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          respond(exchange, "{\"counter\":{\"id\":123456}}");
        });
    server.createContext(
        "/stat/v1/data",
        exchange -> {
          String query = exchange.getRequestURI().getQuery();
          if (query.contains("dimensions=ym:s:date")) {
            respond(
                exchange,
                "{\"data\":[{\"dimensions\":[{\"name\":\"2026-10-20\"}],"
                    + "\"metrics\":[[100,80,250,40,25,2.5,180,12,12]]}],"
                    + "\"totals\":[[100,80,250,40,25,2.5,180,12,12]]}");
          } else {
            respond(
                exchange,
                "{\"data\":[{\"dimensions\":[{\"name\":\"Поисковые системы\"}],"
                    + "\"metrics\":[[70,55,9]]}],\"totals\":[[70,55,9]]}");
          }
        });
    server.start();
    apiUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void activatesCounterAndReturnsOnlyAggregatedMetricaReports() throws Exception {
    mvc.perform(
            put("/api/v1/integrations/yandex_metrica/settings")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiUrl\":\"" + apiUrl + "\",\"counterId\":\"123456\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.settings.counterId").value("123456"));
    mvc.perform(
            put("/api/v1/integrations/yandex_metrica/secret")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oauthToken\":\"metrica-secret-token\"}"))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("metrica-secret-token"))));
    mvc.perform(post("/api/v1/integrations/yandex_metrica/activate").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    assertThat(authorization.get()).isEqualTo("OAuth metrica-secret-token");

    mvc.perform(
            get("/api/v1/analytics/acquisition")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .with(staff.role("MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.web.state").value("READY"))
        .andExpect(jsonPath("$.web.traffic.visits").value(100))
        .andExpect(jsonPath("$.web.traffic.users").value(80))
        .andExpect(jsonPath("$.web.traffic.goalReaches").value(12))
        .andExpect(jsonPath("$.web.daily[0].date").value("2026-10-20"))
        .andExpect(jsonPath("$.web.sources[0].source").value("Поисковые системы"))
        .andExpect(content().string(not(containsString("metrica-secret-token"))))
        .andExpect(content().string(not(containsString("ClientID"))));
  }

  @Test
  void uploadsAttributedBookingOnceAndSkipsCancelledAppointments() throws Exception {
    mvc.perform(
            put("/api/v1/integrations/yandex_metrica/settings")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"apiUrl\":\""
                        + apiUrl
                        + "\",\"counterId\":\"123456\",\"offlineGoalId\":\"789\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.settings.offlineGoalId").value("789"));
    mvc.perform(
            put("/api/v1/integrations/yandex_metrica/secret")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oauthToken\":\"metrica-secret-token\"}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/integrations/yandex_metrica/activate").with(staff.admin()))
        .andExpect(status().isOk());

    mvc.perform(
            post("/api/v1/appointments")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"patientId":"00000000-0000-0000-0000-000000000102",
                    "practitionerId":"00000000-0000-0000-0000-000000000201",
                    "startsAt":"2026-11-03T09:00:00Z","endsAt":"2026-11-03T09:30:00Z",
                    "serviceName":"Повторный приём","metricaClientId":"133591247640966458"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.metricaClientId").doesNotExist())
        .andExpect(content().string(not(containsString("133591247640966458"))));
    String cancelled =
        mvc.perform(
                post("/api/v1/appointments")
                    .with(staff.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"patientId":"00000000-0000-0000-0000-000000000102",
                        "practitionerId":"00000000-0000-0000-0000-000000000201",
                        "startsAt":"2026-11-03T10:00:00Z","endsAt":"2026-11-03T10:30:00Z",
                        "serviceName":"Повторный приём","metricaClientId":"579124169844706072"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String cancelledId = com.jayway.jsonpath.JsonPath.read(cancelled, "$.id");
    mvc.perform(post("/api/v1/appointments/" + cancelledId + "/cancel").with(staff.admin()))
        .andExpect(status().isOk());

    mvc.perform(get("/api/v1/integrations/yandex_metrica/offline-conversions").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.configured").value(true))
        .andExpect(jsonPath("$.attributed").value(2))
        .andExpect(jsonPath("$.pending").value(1))
        .andExpect(jsonPath("$.uploaded").value(0));
    mvc.perform(
            post("/api/v1/integrations/yandex_metrica/offline-conversions/upload")
                .with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uploaded").value(1))
        .andExpect(jsonPath("$.uploadingId").value("upload-42"))
        .andExpect(content().string(not(containsString("133591247640966458"))));

    assertThat(uploadBody.get())
        .contains("ClientId,Target,DateTime,Price,Currency")
        .contains("133591247640966458,789,")
        .contains("3000.00,RUB")
        .doesNotContain("579124169844706072");
    assertThat(authorization.get()).isEqualTo("OAuth metrica-secret-token");

    mvc.perform(
            post("/api/v1/integrations/yandex_metrica/offline-conversions/upload")
                .with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.uploaded").value(0));
    assertThat(uploadCalls.get()).isEqualTo(1);
    mvc.perform(get("/api/v1/integrations/yandex_metrica/offline-conversions").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pending").value(0))
        .andExpect(jsonPath("$.uploaded").value(1))
        .andExpect(jsonPath("$.failed").value(0));
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
