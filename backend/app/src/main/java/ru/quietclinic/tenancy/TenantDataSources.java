package ru.quietclinic.tenancy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Ленивые пулы клиник: один пул на базу, управляющий пул остаётся отдельным. */
@Component
class TenantDataSources {
  private final DataSource control;
  private final TenantDirectory directory;
  private final boolean enabled;
  private final int maximumPoolSize;
  private final Map<UUID, HikariDataSource> pools = new ConcurrentHashMap<>();

  TenantDataSources(
      @Qualifier("controlDataSource") DataSource control,
      @org.springframework.context.annotation.Lazy TenantDirectory directory,
      @Value("${clinic.tenancy.enabled:false}") boolean enabled,
      @Value("${clinic.tenancy.maximum-pool-size:5}") int maximumPoolSize) {
    this.control = control;
    this.directory = directory;
    this.enabled = enabled;
    this.maximumPoolSize = maximumPoolSize;
  }

  DataSource get(UUID tenantId) {
    if (!enabled || tenantId == null) return control;
    return pools.computeIfAbsent(tenantId, this::create);
  }

  DataSource createForProvisioning(UUID tenantId) {
    return pools.computeIfAbsent(tenantId, id -> create(id, directory.databaseForProvisioning(id)));
  }

  void setStatus(UUID tenantId, String status) {
    directory.setStatus(tenantId, status);
  }

  private HikariDataSource create(UUID tenantId) {
    return create(tenantId, directory.database(tenantId));
  }

  private HikariDataSource create(UUID tenantId, TenantDatabase database) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(database.url());
    config.setUsername(database.username());
    config.setPassword(database.password());
    config.setMaximumPoolSize(maximumPoolSize);
    config.setPoolName("tenant-" + tenantId);
    return new HikariDataSource(config);
  }

  @PreDestroy
  void close() {
    pools.values().forEach(HikariDataSource::close);
  }
}
