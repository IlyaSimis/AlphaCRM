package ru.quietclinic.integrations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class IntegrationRepository {
  private final JdbcTemplate jdbc;

  IntegrationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  List<IntegrationRecord> list(UUID clinicId) {
    return jdbc.query(
        "select id, clinic_id, type, status, public_key, settings, active_secret_version"
            + " from integration where clinic_id = ? order by type",
        this::map,
        clinicId);
  }

  Optional<IntegrationRecord> find(UUID clinicId, String type) {
    return jdbc
        .query(
            "select id, clinic_id, type, status, public_key, settings, active_secret_version"
                + " from integration where clinic_id = ? and type = ?",
            this::map,
            clinicId,
            type)
        .stream()
        .findFirst();
  }

  Optional<IntegrationRecord> findByPublicKey(UUID publicKey, String type) {
    return jdbc
        .query(
            "select id, clinic_id, type, status, public_key, settings, active_secret_version"
                + " from integration where public_key = ? and type = ?",
            this::map,
            publicKey,
            type)
        .stream()
        .findFirst();
  }

  IntegrationRecord create(UUID clinicId, String type, String settings) {
    var id = UUID.randomUUID();
    var now = Timestamp.from(Instant.now());
    jdbc.update(
        "insert into integration(id, clinic_id, type, status, public_key, settings, created_at,"
            + " updated_at) values(?, ?, ?, 'PENDING', ?, ?, ?, ?)",
        id,
        clinicId,
        type,
        UUID.randomUUID(),
        settings,
        now,
        now);
    return find(clinicId, type).orElseThrow();
  }

  void updateSettings(UUID id, String settings) {
    jdbc.update(
        "update integration set settings = ?, updated_at = ? where id = ?",
        settings,
        Timestamp.from(Instant.now()),
        id);
  }

  void saveSecret(UUID integrationId, int version, IntegrationSecretCipher.Sealed sealed) {
    jdbc.update(
        "insert into integration_secret(integration_id, version, ciphertext, wrapped_data_key,"
            + " key_version, created_at) values(?, ?, ?, ?, ?, ?)",
        integrationId,
        version,
        sealed.ciphertext(),
        sealed.wrappedDataKey(),
        sealed.keyVersion(),
        Timestamp.from(Instant.now()));
    jdbc.update(
        "update integration set active_secret_version = ?, updated_at = ? where id = ?",
        version,
        Timestamp.from(Instant.now()),
        integrationId);
  }

  Optional<IntegrationSecretRecord> findSecret(UUID integrationId, int version) {
    return jdbc
        .query(
            "select ciphertext, wrapped_data_key, key_version from integration_secret"
                + " where integration_id = ? and version = ?",
            (rs, row) ->
                new IntegrationSecretRecord(
                    rs.getBytes("ciphertext"),
                    rs.getBytes("wrapped_data_key"),
                    rs.getString("key_version")),
            integrationId,
            version)
        .stream()
        .findFirst();
  }

  void updateStatus(UUID id, String status) {
    jdbc.update(
        "update integration set status = ?, updated_at = ? where id = ?",
        status,
        Timestamp.from(Instant.now()),
        id);
  }

  private IntegrationRecord map(ResultSet rs, int row) throws SQLException {
    return new IntegrationRecord(
        rs.getObject("id", UUID.class),
        rs.getObject("clinic_id", UUID.class),
        rs.getString("type"),
        rs.getString("status"),
        rs.getObject("public_key", UUID.class),
        rs.getString("settings"),
        rs.getObject("active_secret_version", Integer.class));
  }
}
