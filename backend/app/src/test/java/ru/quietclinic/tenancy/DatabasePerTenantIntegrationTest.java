package ru.quietclinic.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.quietclinic.StaffSessions;
import ru.quietclinic.integrations.MessengerIntegrationClientFactory;

/** Полный вертикальный срез: две физические H2, вход по каталогу и изоляция доменных записей. */
@SpringBootTest(
    properties = {
      "clinic.tenancy.enabled=true",
      "clinic.tenancy.provisioning-token=test-control-token",
      "clinic.events.enabled=false",
      "clinic.marking.relay-enabled=false"
    })
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DatabasePerTenantIntegrationTest {
  private static final String NEW_TENANT =
      """
      {"name":"Second Clinic","adminFullName":"Second Owner",
       "adminUsername":"owner.second","adminPassword":"temporary-pass"}
      """;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Autowired
  @Qualifier("controlJdbcTemplate")
  JdbcTemplate control;

  @Autowired MessengerIntegrationClientFactory messengerClients;
  @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

  @Autowired
  org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean entityManagerFactoryBean;

  @MockitoBean TenantDatabaseManager databases;
  StaffSessions staff;

  @BeforeEach
  void setUp() {
    staff = new StaffSessions(mvc);
    when(databases.create(any(UUID.class)))
        .thenAnswer(
            invocation -> {
              UUID id = invocation.getArgument(0);
              return new TenantDatabase(
                  "jdbc:h2:mem:tenant-"
                      + id
                      + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;"
                      + "DB_CLOSE_DELAY=-1",
                  "sa",
                  "tenant-test-password");
            });
  }

  @Test
  void provisionsSignsInAndKeepsDomainDataOutOfControlDatabase() throws Exception {
    assertThat(entityManagerFactoryBean.getDataSource()).isSameAs(jdbc.getDataSource());
    assertThat(control.queryForObject("select count(*) from tenant_catalog", Long.class)).isOne();

    mvc.perform(
            post("/api/v1/tenancy/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(NEW_TENANT))
        .andExpect(status().isUnauthorized());
    String created =
        provision(NEW_TENANT)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID tenantId = UUID.fromString(JsonPath.read(created, "$.id"));

    assertThat(
            control.queryForObject(
                "select tenant_id from tenant_login where username = 'owner.second'", UUID.class))
        .isEqualTo(tenantId);
    assertThat(
            TenantScope.call(
                tenantId,
                () ->
                    jdbc.queryForObject(
                        "select count(*) from staff_user where username = 'owner.second'",
                        Long.class)))
        .isOne();
    String passwordHash =
        TenantScope.call(
            tenantId,
            () ->
                jdbc.queryForObject(
                    "select password_hash from staff_user where username = 'owner.second'",
                    String.class));
    assertThat(
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .matches("temporary-pass", passwordHash))
        .isTrue();
    Long jpaCount =
        TenantScope.call(
            tenantId,
            () -> {
              var entityManager = entityManagerFactory.createEntityManager();
              try {
                return entityManager
                    .createQuery(
                        "select count(u) from StaffUser u where u.username = :username", Long.class)
                    .setParameter("username", "owner.second")
                    .getSingleResult();
              } finally {
                entityManager.close();
              }
            });
    assertThat(jpaCount).isOne();
    StaffSessions.Login owner = login("owner.second", "temporary-pass");
    mvc.perform(get("/api/v1/auth/me").with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clinicName").value("Second Clinic"));
    staff.changePassword(owner, "temporary-pass", "owner-password");

    String integration =
        mvc.perform(
                put("/api/v1/integrations/telegram/settings")
                    .with(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"apiUrl\":\"https://api.telegram.org\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID publicKey = UUID.fromString(JsonPath.read(integration, "$.publicKey"));
    mvc.perform(
            put("/api/v1/integrations/telegram/secret")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"botToken\":\"second-clinic-token\",\"webhookSecret\":\"second-hook\"}"))
        .andExpect(status().isOk());
    mvc.perform(post("/api/v1/integrations/telegram/activate").with(owner))
        .andExpect(status().isOk());

    assertThat(messengerClients.webhook(publicKey, "TELEGRAM")).isPresent();
    assertThat(messengerClients.webhook(publicKey, "TELEGRAM").orElseThrow().clinicId())
        .isEqualTo(tenantId);
    assertThat(
            control.queryForObject(
                "select tenant_id from tenant_webhook where public_key = ? and type = 'TELEGRAM'",
                UUID.class,
                publicKey))
        .isEqualTo(tenantId);

    assertThat(jdbc.queryForObject("select count(*) from integration", Long.class)).isZero();
    assertThat(
            TenantScope.call(
                tenantId,
                () -> jdbc.queryForObject("select count(*) from integration", Long.class)))
        .isOne();
    assertThat(control.queryForObject("select count(*) from staff_user", Long.class)).isOne();
    assertThat(
            TenantScope.call(
                tenantId, () -> jdbc.queryForObject("select count(*) from staff_user", Long.class)))
        .isOne();

    provision(NEW_TENANT)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
  }

  private ResultActions provision(String body) throws Exception {
    return mvc.perform(
        post("/api/v1/tenancy/tenants")
            .header(TenantApiTokenFilter.HEADER, "test-control-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private StaffSessions.Login login(String username, String password) throws Exception {
    String csrf = staff.csrfToken();
    var result =
        mvc.perform(
                post("/api/v1/auth/login")
                    .cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", csrf))
                    .header("X-XSRF-TOKEN", csrf)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andReturn();
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(200);
    var session =
        (org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false);
    return new StaffSessions.Login(session, csrf);
  }
}
