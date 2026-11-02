package ru.quietclinic.registry;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.StaffSessions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class RegistryWorkQueueControllerTest {
  private static final String MARIA = "00000000-0000-0000-0000-000000000103";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void collectsConfirmationPaymentAndRebookingActions() throws Exception {
    UUID confirmation = UUID.randomUUID();
    UUID completed = UUID.randomUUID();
    UUID charge = UUID.randomUUID();
    UUID patient = UUID.randomUUID();
    jdbc.update(
        "insert into patient(id, full_name, birth_date, created_at, clinic_id)"
            + " values (?, 'Пациент без следующей записи', DATE '1990-01-01', CURRENT_TIMESTAMP,"
            + " '00000000-0000-0000-0000-00000000c001')",
        patient);
    insertAppointment(confirmation, MARIA, "2026-10-20 17:00:00+00", "2026-10-20 17:30:00+00", "BOOKED");
    insertAppointment(completed, patient.toString(), "2026-10-20 18:00:00+00", "2026-10-20 18:30:00+00", "COMPLETED");
    jdbc.update(
        "insert into charge(id, appointment_id, amount_minor, currency, status, created_at)"
            + " values (?, ?, 300000, 'RUB', 'PENDING', CURRENT_TIMESTAMP)",
        charge,
        completed);

    mvc.perform(get("/api/v1/registry/work-queue").param("date", "2026-10-20").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.date").value("2026-10-20"))
        .andExpect(jsonPath("$.items[?(@.appointmentId == '" + confirmation + "')].kind").value(hasItem("CONFIRMATION")))
        .andExpect(jsonPath("$.items[?(@.appointmentId == '" + completed + "')].kind").value(hasItem("PAYMENT")))
        .andExpect(jsonPath("$.items[?(@.appointmentId == '" + completed + "')].kind").value(hasItem("REBOOK")))
        .andExpect(jsonPath("$.items[?(@.appointmentId == '" + completed + "')].patientName").value(hasItem("Пациент без следующей записи")));

    mvc.perform(get("/api/v1/registry/work-queue").param("date", "2026-10-20").with(staff.role("MANAGER")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/registry/work-queue").param("date", "2026-10-21").with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].appointmentId").value(not(hasItem(confirmation.toString()))));
  }

  private void insertAppointment(
      UUID id, String patientId, String startsAt, String endsAt, String status) {
    jdbc.update(
        "insert into appointment(id, patient_id, practitioner_id, starts_at, ends_at, service_name, status, created_at, version)"
            + " values (?, ?, ?, timestamp with time zone '" + startsAt + "',"
            + " timestamp with time zone '" + endsAt + "',"
            + " 'Повторный приём', ?, CURRENT_TIMESTAMP, 0)",
        id,
        UUID.fromString(patientId),
        UUID.fromString(StaffSessions.PETROV),
        status);
  }
}
