package ru.quietclinic.staff;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.platform.RolePermissions;

/**
 * Первый старт: если учётных записей ещё нет, создаёт клинику (CLINIC_NAME, по умолчанию AlphaCRM)
 * и её администратора с логином {@code admin} и паролем из CLINIC_ADMIN_PASSWORD; ФИО
 * администратора — из CLINIC_ADMIN_NAME. Хеш считается здесь же, bcrypt со своей солью; ни пароля,
 * ни хеша нет ни в коде, ни в миграциях. Начальная учётная запись — владелец клиники (OWNER):
 * только он назначает старших администраторов. Дальше сотрудников заводит администратор.
 */
@Component
class ClinicBootstrap implements ApplicationRunner {
  static final String ADMIN_USERNAME = "admin";
  static final String DEFAULT_CLINIC_NAME = "AlphaCRM";
  static final String DEFAULT_ADMIN_FULL_NAME = "Administrator";
  private static final Logger log = LoggerFactory.getLogger(ClinicBootstrap.class);

  private final StaffRepository staff;
  private final ClinicRepository clinics;
  private final PasswordEncoder encoder;
  private final ru.quietclinic.tenancy.TenantDirectory directory;
  private final String clinicName;
  private final String adminFullName;
  private final String adminPassword;
  private final boolean adminMustChangePassword;

  ClinicBootstrap(
      StaffRepository staff,
      ClinicRepository clinics,
      PasswordEncoder encoder,
      ru.quietclinic.tenancy.TenantDirectory directory,
      @Value("${clinic.name:AlphaCRM}") String clinicName,
      @Value("${clinic.admin-full-name:Administrator}") String adminFullName,
      @Value("${clinic.admin-password:}") String adminPassword,
      @Value("${clinic.admin-must-change-password:true}") boolean adminMustChangePassword) {
    this.staff = staff;
    this.clinics = clinics;
    this.encoder = encoder;
    this.directory = directory;
    this.clinicName = clinicName;
    this.adminFullName = adminFullName;
    this.adminPassword = adminPassword;
    this.adminMustChangePassword = adminMustChangePassword;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    String name =
        clinicName == null || clinicName.isBlank() ? DEFAULT_CLINIC_NAME : clinicName.trim();
    // Демо-врачи и пациенты из миграций уже лежат в первой клинике — администратор достаётся ей.
    Clinic clinic =
        clinics.findAllByOrderByCreatedAt().stream()
            .findFirst()
            .orElseGet(() -> clinics.save(new Clinic(name)));
    if (staff.count() == 0) {
      if (adminPassword == null || adminPassword.isBlank()) {
        throw new IllegalStateException(
            "No staff in the database: set CLINIC_ADMIN_PASSWORD for the initial administrator");
      }
      String fullName =
          adminFullName == null || adminFullName.isBlank()
              ? DEFAULT_ADMIN_FULL_NAME
              : adminFullName.trim();
      clinic.name = name;
      staff.save(
          new StaffUser(
              UUID.randomUUID(),
              clinic.id,
              fullName,
              ADMIN_USERNAME,
              encoder.encode(adminPassword),
              RolePermissions.OWNER,
              null,
              adminMustChangePassword));
      log.info("Created owner '{}' for clinic '{}'", ADMIN_USERNAME, name);
    }
    directory.registerDefault(
        clinic.id,
        clinic.name,
        staff.findByClinicIdOrderByFullName(clinic.id).stream()
            .map(user -> user.username)
            .toList());
  }
}
