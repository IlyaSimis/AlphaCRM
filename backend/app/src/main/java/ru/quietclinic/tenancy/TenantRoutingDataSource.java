package ru.quietclinic.tenancy;

import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.quietclinic.platform.ClinicMember;

/** Выбирает БД только из server-side scope или аутентифицированного principal. */
class TenantRoutingDataSource extends AbstractRoutingDataSource {
  private final TenantDataSources dataSources;

  TenantRoutingDataSource(TenantDataSources dataSources) {
    this.dataSources = dataSources;
  }

  @Override
  protected Object determineCurrentLookupKey() {
    UUID scoped = TenantScope.current();
    if (scoped != null) return scoped;
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof ClinicMember member) {
      return member.clinicId();
    }
    return null;
  }

  @Override
  protected DataSource determineTargetDataSource() {
    return dataSources.get((UUID) determineCurrentLookupKey());
  }
}
