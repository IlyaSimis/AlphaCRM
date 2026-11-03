package ru.quietclinic.communications;

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
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.StaffSessions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class NotificationSettingsControllerTest {
  private static final String ROUTE = "/api/v1/clinic/notification-settings";

  @Autowired MockMvc mvc;
  StaffSessions staff;

  @BeforeEach
  void setUp() {
    staff = new StaffSessions(mvc);
  }

  @Test
  void defaultsToDailyReminderAndSavesBothSchedules() throws Exception {
    mvc.perform(get(ROUTE).with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reminder24hEnabled").value(true))
        .andExpect(jsonPath("$.reminder3hEnabled").value(false));

    mvc.perform(
            put(ROUTE)
                .with(staff.admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reminder24hEnabled\":true,\"reminder3hEnabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reminder24hEnabled").value(true))
        .andExpect(jsonPath("$.reminder3hEnabled").value(true));

    mvc.perform(get(ROUTE).with(staff.admin()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reminder24hEnabled").value(true))
        .andExpect(jsonPath("$.reminder3hEnabled").value(true));
  }

  @Test
  void rejectsRolesWithoutIntegrationPermissions() throws Exception {
    mvc.perform(get(ROUTE).with(staff.role("CASHIER"))).andExpect(status().isForbidden());
    mvc.perform(
            put(ROUTE)
                .with(staff.role("CASHIER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reminder24hEnabled\":false,\"reminder3hEnabled\":false}"))
        .andExpect(status().isForbidden());
  }
}
