package ru.quietclinic.staff;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.quietclinic.StaffSessions;

/**
 * Ставки (раздел C, hr): чужие — только владелец, свои — каждый; новые условия закрывают прежние.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HrControllerTest {
  @Autowired MockMvc mvc;
  StaffSessions staff;

  @BeforeEach
  void signIn() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void ownerSetsTermsAndEveryoneSeesOnlyTheirOwn() throws Exception {
    var cashier = staff.exactly("CASHIER");
    var id = staff.userId("t.cashier");
    terms(id, "2026-01-01", 5000000).andExpect(status().isOk());
    terms(id, "2026-01-01", 5500000)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONTRACT_DATE_TOO_EARLY"));
    terms(id, "2026-06-01", 5500000).andExpect(status().isOk());

    mvc.perform(get("/api/v1/hr/contracts/me").with(cashier))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].rateMinor").value(5500000))
        .andExpect(jsonPath("$[1].validTo").value("2026-05-31"));
    mvc.perform(get("/api/v1/hr/contracts/" + id).with(cashier)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/hr/contracts").with(staff.exactly("SENIOR_ADMINISTRATOR")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/hr/contracts").with(staff.admin())).andExpect(status().isOk());
  }

  private org.springframework.test.web.servlet.ResultActions terms(
      String id, String from, long rate) throws Exception {
    return mvc.perform(
        put("/api/v1/hr/contracts/" + id)
            .with(staff.admin())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"position":"Кассир","rateMinor":%d,"rateUnit":"MONTH","validFrom":"%s"}
                """
                    .formatted(rate, from)));
  }
}
