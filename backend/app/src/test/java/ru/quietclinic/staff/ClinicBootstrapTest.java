package ru.quietclinic.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class ClinicBootstrapTest {
  final StaffRepository staff = mock(StaffRepository.class);
  final ClinicRepository clinics = mock(ClinicRepository.class);
  final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

  ClinicBootstrap bootstrap(String name, String password, boolean mustChange) {
    return new ClinicBootstrap(
        staff, clinics, encoder, name, "Clinic Administrator", password, mustChange);
  }

  @Test
  void emptyDatabaseGetsOneClinicAndAdministratorWithSaltedHash() {
    when(staff.count()).thenReturn(0L);
    when(clinics.findAllByOrderByCreatedAt()).thenReturn(List.of());
    when(clinics.save(any(Clinic.class))).thenAnswer(call -> call.getArgument(0));

    bootstrap("Sadovaya Street Clinic", "strong-admin-pass", true).run(null);

    var clinic = ArgumentCaptor.forClass(Clinic.class);
    verify(clinics).save(clinic.capture());
    assertThat(clinic.getValue().name).isEqualTo("Sadovaya Street Clinic");
    var admin = ArgumentCaptor.forClass(StaffUser.class);
    verify(staff).save(admin.capture());
    assertThat(admin.getValue().username).isEqualTo("admin");
    // Начальная учётная запись — владелец клиники (раздел C целевой архитектуры).
    assertThat(admin.getValue().role).isEqualTo("OWNER");
    assertThat(admin.getValue().fullName).isEqualTo("Clinic Administrator");
    assertThat(admin.getValue().clinicId).isEqualTo(clinic.getValue().id);
    assertThat(admin.getValue().mustChangePassword).isTrue();
    assertThat(admin.getValue().passwordHash).startsWith("$2").doesNotContain("strong-admin-pass");
    assertThat(encoder.matches("strong-admin-pass", admin.getValue().passwordHash)).isTrue();
  }

  @Test
  void eachStartComputesItsOwnSalt() {
    when(clinics.findAllByOrderByCreatedAt()).thenReturn(List.of(new Clinic("Seed Clinic")));
    bootstrap("AlphaCRM", "admin", false).run(null);
    bootstrap("AlphaCRM", "admin", false).run(null);
    var admins = ArgumentCaptor.forClass(StaffUser.class);
    verify(staff, org.mockito.Mockito.times(2)).save(admins.capture());
    assertThat(admins.getAllValues().get(0).passwordHash)
        .isNotEqualTo(admins.getAllValues().get(1).passwordHash);
    assertThat(admins.getAllValues().get(0).mustChangePassword).isFalse();
  }

  @Test
  void existingStaffMeansNothingIsCreated() {
    when(staff.count()).thenReturn(3L);
    bootstrap("AlphaCRM", "", true).run(null);
    verify(staff, never()).save(any());
    verify(clinics, never()).save(any());
  }

  @Test
  void emptyDatabaseWithoutPasswordDoesNotStart() {
    when(staff.count()).thenReturn(0L);
    assertThatThrownBy(() -> bootstrap("AlphaCRM", " ", true).run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CLINIC_ADMIN_PASSWORD");
  }

  @Test
  void clinicFromMigrationsGetsTheConfiguredName() {
    var legacy = new Clinic("Seed Clinic");
    when(clinics.findAllByOrderByCreatedAt()).thenReturn(List.of(legacy));
    bootstrap("Renamed Clinic", "admin", false).run(null);
    assertThat(legacy.name).isEqualTo("Renamed Clinic");
    verify(clinics, never()).save(any());
  }

  @Test
  void blankNamesFallBackToAlphaCrmAndDefaultAdministratorName() {
    var legacy = new Clinic("Seed Clinic");
    when(clinics.findAllByOrderByCreatedAt()).thenReturn(List.of(legacy));
    new ClinicBootstrap(staff, clinics, encoder, " ", "", "admin", false).run(null);
    assertThat(legacy.name).isEqualTo("AlphaCRM");
    var admin = ArgumentCaptor.forClass(StaffUser.class);
    verify(staff).save(admin.capture());
    assertThat(admin.getValue().fullName).isEqualTo("Administrator");
  }
}
