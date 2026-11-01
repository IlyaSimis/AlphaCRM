package ru.quietclinic.patients;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.quietclinic.StaffSessions;
import ru.quietclinic.patients.PostgresPatientReader.ImportedPatient;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PatientImportControllerTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean PostgresPatientReader reader;
  StaffSessions staff;

  @BeforeEach
  void setUp() {
    staff = new StaffSessions(mvc);
    when(reader.read(any()))
        .thenReturn(
            List.of(
                new ImportedPatient(
                    "Imported One", "1990-01-02", "+7 900 000-00-01", "", "one@example.ru"),
                new ImportedPatient("Duplicate Anna", "1991-05-14", "8 916 245-18-72", "", ""),
                new ImportedPatient("Invalid Date", "2999-01-01", "+7 900 000-00-03", "", "")));
  }

  @Test
  void previewDoesNotSaveAndSeparatesNewDuplicatesAndErrors() throws Exception {
    when(reader.read(any()))
        .thenReturn(
            List.of(
                new ImportedPatient(
                    "Preview One", "1990-01-02", "+7 900 000-00-11", "", "one@example.ru"),
                new ImportedPatient("Duplicate Anna", "1991-05-14", "8 916 245-18-72", "", ""),
                new ImportedPatient("Invalid Date", "2999-01-01", "+7 900 000-00-03", "", "")));
    mvc.perform(
            post("/api/v1/patients/import/postgresql/preview")
                .with(staff.role("MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.created").value(1))
        .andExpect(jsonPath("$.duplicates").value(1))
        .andExpect(jsonPath("$.errors").value(1))
        .andExpect(jsonPath("$.rows[0].status").value("NEW"))
        .andExpect(jsonPath("$.rows[1].reason").value("DUPLICATE_IN_CRM"));
    Integer count =
        jdbc.queryForObject(
            "select count(*) from patient where full_name = 'Preview One'", Integer.class);
    Assertions.assertThat(count).isZero();
  }

  @Test
  void commitCreatesOnlyNewCardsWithoutInventedConsentAndIsRepeatable() throws Exception {
    mvc.perform(
            post("/api/v1/patients/import/postgresql/commit")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(1));
    Integer count =
        jdbc.queryForObject(
            "select count(*) from patient p"
                + " left join patient_consent c on c.patient_id = p.id where p.full_name = 'Imported One'"
                + " and c.id is null",
            Integer.class);
    Assertions.assertThat(count).isEqualTo(1);
    mvc.perform(
            post("/api/v1/patients/import/postgresql/commit")
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(0))
        .andExpect(jsonPath("$.duplicates").value(2));
  }

  @Test
  void cashierCannotImportPatients() throws Exception {
    mvc.perform(
            post("/api/v1/patients/import/postgresql/preview")
                .with(staff.role("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(request()))
        .andExpect(status().isForbidden());
  }

  private static String request() {
    return """
        {"url":"jdbc:postgresql://localhost:5432/source","username":"readonly","password":"secret",
         "table":"public.patients","columns":{"fullName":"full_name","birthDate":"birth_date",
         "phone":"phone","secondPhone":"second_phone","email":"email"},"limit":1000}
        """;
  }
}
