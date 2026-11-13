package ru.quietclinic.tenancy;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Управляющий каталог: login/publicKey → клиника и зашифрованный маршрут к её БД. */
@Repository
public class TenantDirectory {
  private final JdbcTemplate control;
  private final TenantCredentialCipher cipher;
  private final DataSourceProperties defaultDatabase;
  private final boolean enabled;

  TenantDirectory(
      @Qualifier("controlJdbcTemplate") JdbcTemplate control,
      TenantCredentialCipher cipher,
      DataSourceProperties defaultDatabase,
      @Value("${clinic.tenancy.enabled:false}") boolean enabled) {
    this.control = control;
    this.cipher = cipher;
    this.defaultDatabase = defaultDatabase;
    this.enabled = enabled;
  }

  public boolean enabled() {
    return enabled;
  }

  public Optional<UUID> clinicForLogin(String username) {
    if (!enabled) return Optional.empty();
    return control
        .query(
            "select tenant_id from tenant_login where username = ?",
            (rs, row) -> rs.getObject("tenant_id", UUID.class),
            username)
        .stream()
        .findFirst();
  }

  public Optional<UUID> clinicForWebhook(UUID publicKey, String type) {
    if (!enabled) return Optional.empty();
    return control
        .query(
            "select tenant_id from tenant_webhook where public_key = ? and type = ?",
            (rs, row) -> rs.getObject("tenant_id", UUID.class),
            publicKey,
            type)
        .stream()
        .findFirst();
  }

  public List<TenantRoute> activeTenants() {
    if (!enabled) return List.of();
    return control.query(
        "select id, name, status from tenant_catalog where status = 'ACTIVE' order by created_at",
        this::route);
  }

  public void requireLoginAvailable(String username) {
    if (enabled && clinicForLogin(username).isPresent()) {
      throw conflict("USERNAME_TAKEN", "Username is already taken");
    }
  }

  public void registerLogin(UUID tenantId, String username) {
    if (!enabled || clinicForLogin(username).isPresent()) return;
    try {
      control.update(
          "insert into tenant_login(username, tenant_id, created_at) values(?, ?, ?)",
          username,
          tenantId,
          Timestamp.from(Instant.now()));
    } catch (org.springframework.dao.DuplicateKeyException ignored) {
      // Параллельный запуск уже зарегистрировал глобально уникальный логин.
    }
  }

  public void registerWebhook(UUID tenantId, UUID publicKey, String type) {
    if (!enabled) return;
    int updated =
        control.update(
            "update tenant_webhook set tenant_id = ? where public_key = ? and type = ?",
            tenantId,
            publicKey,
            type);
    if (updated != 0) return;
    try {
      control.update(
          "insert into tenant_webhook(public_key, type, tenant_id) values(?, ?, ?)",
          publicKey,
          type,
          tenantId);
    } catch (org.springframework.dao.DuplicateKeyException ignored) {
      control.update(
          "update tenant_webhook set tenant_id = ? where public_key = ? and type = ?",
          tenantId,
          publicKey,
          type);
    }
  }

  public void registerDefault(UUID tenantId, String name, List<String> usernames) {
    if (!enabled) return;
    TenantDatabase database =
        new TenantDatabase(
            defaultDatabase.getUrl(), defaultDatabase.getUsername(), defaultDatabase.getPassword());
    registerDatabase(tenantId, name, database, "ACTIVE");
    usernames.forEach(username -> registerLogin(tenantId, username));
  }

  void registerDatabase(UUID tenantId, String name, TenantDatabase database, String status) {
    if (!enabled) return;
    if (updateDatabaseRoute(tenantId, name, database, status) != 0) return;
    TenantCredentialCipher.Sealed sealed = cipher.seal(tenantId, database.password());
    try {
      control.update(
          "insert into tenant_catalog(id, name, status, database_url, database_username,"
              + " password_ciphertext, wrapped_data_key, key_version, created_at)"
              + " values(?, ?, ?, ?, ?, ?, ?, ?, ?)",
          tenantId,
          name,
          status,
          database.url(),
          database.username(),
          sealed.ciphertext(),
          sealed.wrappedKey(),
          sealed.keyVersion(),
          Timestamp.from(Instant.now()));
    } catch (org.springframework.dao.DuplicateKeyException ignored) {
      updateDatabaseRoute(tenantId, name, database, status);
    }
  }

  private int updateDatabaseRoute(
      UUID tenantId, String name, TenantDatabase database, String status) {
    return control.update(
        "update tenant_catalog set name = ?, status = ?, database_url = ?,"
            + " database_username = ? where id = ?",
        name,
        status,
        database.url(),
        database.username(),
        tenantId);
  }

  void setStatus(UUID tenantId, String status) {
    if (enabled)
      control.update("update tenant_catalog set status = ? where id = ?", status, tenantId);
  }

  TenantDatabase database(UUID tenantId) {
    return database(tenantId, "ACTIVE");
  }

  TenantDatabase databaseForProvisioning(UUID tenantId) {
    return database(tenantId, "PROVISIONING");
  }

  private TenantDatabase database(UUID tenantId, String status) {
    return control
        .query(
            "select database_url, database_username, password_ciphertext, wrapped_data_key,"
                + " key_version from tenant_catalog where id = ? and status = ?",
            (rs, row) ->
                new TenantDatabase(
                    rs.getString("database_url"),
                    rs.getString("database_username"),
                    cipher.open(
                        tenantId,
                        rs.getBytes("password_ciphertext"),
                        rs.getBytes("wrapped_data_key"),
                        rs.getString("key_version"))),
            tenantId,
            status)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> conflict("TENANT_DATABASE_UNAVAILABLE", "Clinic database is unavailable"));
  }

  private TenantRoute route(ResultSet rs, int row) throws SQLException {
    return new TenantRoute(
        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("status"));
  }
}
