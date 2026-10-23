package ru.quietclinic;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Вторая клиника, добавленная строками в базу — без правки кода: своя запись в clinic, свой
 * администратор, врач и пациент. Телефон пациента совпадает с телефоном Анны Михайловой из первой
 * клиники: совпадение между клиниками не должно показываться.
 */
public final class SecondClinic {
  public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000c002");
  public static final String NAME = "Second Clinic";
  public static final String ADMIN_USERNAME = "admin.second";
  public static final String ADMIN_PASSWORD = "second-admin-1";
  public static final String PRACTITIONER = "00000000-0000-0000-0000-00000000c201";
  public static final String PATIENT = "00000000-0000-0000-0000-00000000c101";
  public static final String PATIENT_NAME = "Second Clinic Patient";

  /** Тот же номер, что у Анны Михайловой в первой клинике. */
  public static final String SHARED_PHONE = "+79162451872";

  private SecondClinic() {}

  public static synchronized void ensure(JdbcTemplate jdbc) {
    var exists = jdbc.queryForObject("select count(*) from clinic where id = ?", Long.class, ID);
    if (exists != null && exists > 0) return;
    var now = Timestamp.from(Instant.now());
    jdbc.update("insert into clinic(id, name, created_at) values (?, ?, ?)", ID, NAME, now);
    jdbc.update(
        "insert into staff_user(id, clinic_id, full_name, username, password_hash, role,"
            + " practitioner_id, active, must_change_password, created_at)"
            + " values (?, ?, ?, ?, ?, 'ADMINISTRATOR', null, true, false, ?)",
        UUID.randomUUID(),
        ID,
        "Second Clinic Administrator",
        ADMIN_USERNAME,
        new BCryptPasswordEncoder().encode(ADMIN_PASSWORD),
        now);
    jdbc.update(
        "insert into practitioner(id, clinic_id, full_name, specialty) values (?, ?, ?, ?)",
        UUID.fromString(PRACTITIONER),
        ID,
        "Second Clinic Doctor",
        "Psychiatrist");
    jdbc.update(
        "insert into patient(id, clinic_id, full_name, birth_date, created_at)"
            + " values (?, ?, ?, ?, ?)",
        UUID.fromString(PATIENT),
        ID,
        PATIENT_NAME,
        java.sql.Date.valueOf(LocalDate.parse("1990-01-01")),
        now);
    jdbc.update(
        "insert into patient_contact(id, patient_id, type, contact_value) values (?, ?, ?, ?)",
        UUID.randomUUID(),
        UUID.fromString(PATIENT),
        "PHONE",
        SHARED_PHONE);
  }
}
