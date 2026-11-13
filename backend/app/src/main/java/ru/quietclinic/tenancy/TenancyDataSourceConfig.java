package ru.quietclinic.tenancy;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource;

/** Управляющая БД для каталога/сессий и routing DataSource для доменных данных. */
@Configuration
class TenancyDataSourceConfig {
  @Bean
  @SpringSessionDataSource
  @ConfigurationProperties("spring.datasource.hikari")
  HikariDataSource controlDataSource(DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
  }

  @Bean
  JdbcTemplate controlJdbcTemplate(@Qualifier("controlDataSource") DataSource control) {
    return new JdbcTemplate(control);
  }

  @Bean
  @Primary
  DataSource dataSource(TenantDataSources dataSources) {
    TenantRoutingDataSource routing = new TenantRoutingDataSource(dataSources);
    // Целевые пулы создаются лениво в determineTargetDataSource, статической карты нет.
    routing.setTargetDataSources(java.util.Map.of());
    routing.setLenientFallback(false);
    routing.afterPropertiesSet();
    return routing;
  }

  @Bean
  @Primary
  JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource routing) {
    return new JdbcTemplate(routing);
  }
}
