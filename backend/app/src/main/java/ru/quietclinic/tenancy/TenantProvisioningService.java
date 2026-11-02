package ru.quietclinic.tenancy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.quietclinic.platform.RolePermissions;

/** Создание изолированной клиники: database → Liquibase → владелец → маршруты входа. */
@Service
class TenantProvisioningService {
  private final TenantDatabaseManager databases;
  private final TenantDataSources dataSources;
  private final TenantDirectory directory;
  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;
  private final boolean enabled;
  private final String changeLog;

  TenantProvisioningService(
      TenantDatabaseManager databases,
      TenantDataSources dataSources,
      TenantDirectory directory,
      JdbcTemplate jdbc,
      PasswordEncoder encoder,
      @Value("${clinic.tenancy.enabled:false}") boolean enabled,
      @Value("${spring.liquibase.change-log:classpath:db/changelog/db.changelog-master.yaml}")
          String changeLog) {
    this.databases = databases;
    this.dataSources = dataSources;
    this.directory = directory;
    this.jdbc = jdbc;
    this.encoder = encoder;
    this.enabled = enabled;
    this.changeLog = changeLog;
  }

  List<TenantRoute> list() {
    return directory.activeTenants();
  }

  TenantRoute provision(CreateTenantRequest request) {
    if (!enabled) {
      throw ru.quietclinic.platform.PlatformConfig.conflict(
          "TENANCY_DISABLED", "Database-per-tenant mode is disabled");
    }
    String username = request.adminUsername().trim().toLowerCase(Locale.ROOT);
    directory.requireLoginAvailable(username);
    UUID tenantId = UUID.randomUUID();
    TenantDatabase database = databases.create(tenantId);
    directory.registerDatabase(tenantId, request.name().trim(), database, "PROVISIONING");
    try {
      migrate(tenantId);
      createOwner(tenantId, request, username);
      directory.registerLogin(tenantId, username);
      dataSources.setStatus(tenantId, "ACTIVE");
      return new TenantRoute(tenantId, request.name().trim(), "ACTIVE");
    } catch (RuntimeException error) {
      dataSources.setStatus(tenantId, "ERROR");
      throw error;
    }
  }

  private void migrate(UUID tenantId) {
    try {
      SpringLiquibase liquibase = new SpringLiquibase();
      liquibase.setDataSource(dataSources.createForProvisioning(tenantId));
      liquibase.setChangeLog(changeLog);
      liquibase.afterPropertiesSet();
    } catch (Exception error) {
      throw new IllegalStateException("Tenant database migration failed", error);
    }
  }

  private void createOwner(UUID tenantId, CreateTenantRequest request, String username) {
    TenantScope.run(
        tenantId,
        () -> {
          Timestamp now = Timestamp.from(Instant.now());
          jdbc.update(
              "insert into clinic(id, name, created_at) values(?, ?, ?)",
              tenantId,
              request.name().trim(),
              now);
          UUID staffId = UUID.randomUUID();
          jdbc.update(
              "insert into staff_user(id, clinic_id, full_name, username, password_hash, role,"
                  + " practitioner_id, active, must_change_password, created_at)"
                  + " values(?, ?, ?, ?, ?, ?, null, true, true, ?)",
              staffId,
              tenantId,
              request.adminFullName().trim(),
              username,
              encoder.encode(request.adminPassword()),
              RolePermissions.OWNER,
              now);
          jdbc.update(
              "insert into staff_role(staff_id, role) values(?, ?)",
              staffId,
              RolePermissions.OWNER);
        });
  }
}
