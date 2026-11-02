package ru.quietclinic.tenancy;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.stereotype.Component;

/** PostgreSQL provisioning: отдельные database и login-role с generated-safe именами. */
@Component
class PostgresTenantDatabaseManager implements TenantDatabaseManager {
  private final DataSource control;
  private final String controlUrl;
  private final SecureRandom random = new SecureRandom();

  PostgresTenantDatabaseManager(
      @Qualifier("controlDataSource") DataSource control, DataSourceProperties properties) {
    this.control = control;
    this.controlUrl = properties.getUrl();
  }

  @Override
  public TenantDatabase create(UUID tenantId) {
    if (controlUrl == null || !controlUrl.startsWith("jdbc:postgresql:")) {
      throw new IllegalStateException("Physical database provisioning requires PostgreSQL");
    }
    String suffix = tenantId.toString().replace("-", "");
    String role = "qc_" + suffix;
    String database = "quiet_clinic_" + suffix;
    byte[] secret = new byte[24];
    random.nextBytes(secret);
    String password = HexFormat.of().formatHex(secret);
    try (Connection connection = control.getConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate("create role \"" + role + "\" login password '" + password + "'");
      statement.executeUpdate("create database \"" + database + "\" owner \"" + role + "\"");
      return new TenantDatabase(databaseUrl(database), role, password);
    } catch (Exception error) {
      throw new IllegalStateException("Tenant database provisioning failed", error);
    }
  }

  private String databaseUrl(String database) {
    int authority = "jdbc:postgresql://".length();
    if (!controlUrl.startsWith("jdbc:postgresql://")) {
      throw new IllegalStateException("PostgreSQL control URL must include a host");
    }
    int path = controlUrl.indexOf('/', authority);
    if (path < 0) throw new IllegalStateException("PostgreSQL control URL has no database path");
    int query = controlUrl.indexOf('?', path);
    String prefix = controlUrl.substring(0, path);
    String parameters = query < 0 ? "" : controlUrl.substring(query);
    return prefix + "/" + database + parameters;
  }
}
