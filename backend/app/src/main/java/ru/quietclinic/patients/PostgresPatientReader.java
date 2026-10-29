package ru.quietclinic.patients;

import static ru.quietclinic.platform.PlatformConfig.badRequest;
import static ru.quietclinic.platform.PlatformConfig.conflict;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import ru.quietclinic.patients.PatientImportController.ImportRequest;

/** Только простые идентификаторы и SELECT: произвольный SQL через интерфейс не принимается. */
@Component
class PostgresPatientReader {
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Pattern TABLE =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*");

  List<ImportedPatient> read(ImportRequest request) {
    validate(request);
    var limit = request.limit() == null ? 1000 : request.limit();
    var properties = new Properties();
    properties.setProperty("user", request.username().trim());
    properties.setProperty("password", request.password());
    properties.setProperty("connectTimeout", "5");
    properties.setProperty("socketTimeout", "20");
    properties.setProperty("ApplicationName", "AlphaCRM patient import");
    var sql =
        "select "
            + identifier(request.columns().fullName())
            + " as full_name, "
            + identifier(request.columns().birthDate())
            + " as birth_date, "
            + identifier(request.columns().phone())
            + " as phone, "
            + optionalColumn(request.columns().secondPhone(), "second_phone")
            + ", "
            + optionalColumn(request.columns().email(), "email")
            + " from "
            + request.table();
    try (var connection = DriverManager.getConnection(request.url().trim(), properties)) {
      connection.setReadOnly(true);
      connection.setAutoCommit(false);
      try (var statement = connection.createStatement()) {
        statement.setQueryTimeout(20);
        statement.setMaxRows(limit);
        try (var rows = statement.executeQuery(sql)) {
          var result = new ArrayList<ImportedPatient>();
          while (rows.next()) {
            result.add(
                new ImportedPatient(
                    rows.getString("full_name"),
                    rows.getString("birth_date"),
                    rows.getString("phone"),
                    rows.getString("second_phone"),
                    rows.getString("email")));
          }
          return result;
        }
      } finally {
        connection.rollback();
      }
    } catch (SQLException error) {
      throw conflict("IMPORT_SOURCE_UNAVAILABLE", "Cannot read the source PostgreSQL database");
    }
  }

  private static void validate(ImportRequest request) {
    if (request == null
        || request.url() == null
        || !request.url().trim().startsWith("jdbc:postgresql://")
        || request.url().length() > 1000
        || request.username() == null
        || request.username().isBlank()
        || request.password() == null
        || request.table() == null
        || !TABLE.matcher(request.table()).matches()
        || request.columns() == null
        || (request.limit() != null && (request.limit() < 1 || request.limit() > 5000))) {
      throw badRequest("IMPORT_CONFIG_INVALID", "Check the PostgreSQL import settings");
    }
    identifier(request.columns().fullName());
    identifier(request.columns().birthDate());
    identifier(request.columns().phone());
    optionalColumn(request.columns().secondPhone(), "second_phone");
    optionalColumn(request.columns().email(), "email");
  }

  private static String identifier(String value) {
    if (value == null || !IDENTIFIER.matcher(value).matches()) {
      throw badRequest("IMPORT_CONFIG_INVALID", "Column names must be simple SQL identifiers");
    }
    return value;
  }

  private static String optionalColumn(String value, String alias) {
    return value == null || value.isBlank()
        ? "null as " + alias
        : identifier(value) + " as " + alias;
  }

  record ImportedPatient(
      String fullName, String birthDate, String phone, String secondPhone, String email) {}
}
