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

import java.nio.charset.StandardCharsets;
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
import ru.quietclinic.SecondClinic;
import ru.quietclinic.StaffSessions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class IntegrationControllerTest {
  private static final UUID MAIN_CLINIC = UUID.fromString("00000000-0000-0000-0000-00000000c001");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired MangoIntegrationClientFactory mangoClients;
  StaffSessions staff;

  @BeforeEach
  void setUp() {
    jdbc.update("delete from integration");
    staff = new StaffSessions(mvc);
  }

  @Test
  void managesAndRotatesMangoWithoutReturningSecrets() throws Exception {
    mvc.perform(
            put("/api/v1/integrations/mango/settings")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"apiUrl\":\"https://app.mango-office.ru\",\"defaultExtension\":\"101\",\"lineNumber\":\"74951234567\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.publicKey").isNotEmpty())
        .andExpect(jsonPath("$.hasSecret").value(false))
        .andExpect(jsonPath("$.settings.defaultExtension").value("101"));
    mvc.perform(
            put("/api/v1/integrations/mango/secret")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"clinic-a-key\",\"apiSalt\":\"clinic-a-salt\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasSecret").value(true))
        .andExpect(jsonPath("$.secretVersion").value(1))
        .andExpect(content().string(not(containsString("clinic-a-key"))))
        .andExpect(content().string(not(containsString("clinic-a-salt"))));
    mvc.perform(post("/api/v1/integrations/mango/activate").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    var client = mangoClients.forClinic(MAIN_CLINIC).orElseThrow();
    assertThat(client.configured()).isTrue();
    assertThat(client.defaultExtension()).isEqualTo("101");
    var ciphertext = jdbc.queryForObject("select ciphertext from integration_secret", byte[].class);
    assertThat(new String(ciphertext, StandardCharsets.UTF_8))
        .doesNotContain("clinic-a-key")
        .doesNotContain("clinic-a-salt");
    mvc.perform(
            put("/api/v1/integrations/mango/secret")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"rotated-key\",\"apiSalt\":\"rotated-salt\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.secretVersion").value(2))
        .andExpect(content().string(not(containsString("rotated-key"))));
    mvc.perform(post("/api/v1/integrations/mango/disable").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISABLED"));
    assertThat(mangoClients.forClinic(MAIN_CLINIC).orElseThrow().configured()).isFalse();
  }

  @Test
  void requiresSecretAndRestrictsManagementByRole() throws Exception {
    mvc.perform(
            put("/api/v1/integrations/mango/settings")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"apiUrl\":\"https://app.mango-office.ru\",\"defaultExtension\":\"101\",\"lineNumber\":\"\"}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/integrations/mango/activate").with(staff.admin()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INTEGRATION_SECRET_MISSING"));
    mvc.perform(get("/api/v1/integrations").with(staff.role("CASHIER")))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/v1/integrations/mango/secret")
                .with(staff.role("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"forbidden\",\"apiSalt\":\"forbidden\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void keepsConfigurationInsideItsClinic() throws Exception {
    mvc.perform(
            put("/api/v1/integrations/mango/secret")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"clinic-a-key\",\"apiSalt\":\"clinic-a-salt\"}"))
        .andExpect(status().isOk());
    SecondClinic.ensure(jdbc);
    var secondAdmin = staff.login(SecondClinic.ADMIN_USERNAME, SecondClinic.ADMIN_PASSWORD);
    mvc.perform(get("/api/v1/integrations").with(secondAdmin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    mvc.perform(get("/api/v1/integrations/mango").with(secondAdmin))
        .andExpect(status().isNotFound());
    assertThat(mangoClients.forClinic(SecondClinic.ID)).isEmpty();
  }
}
