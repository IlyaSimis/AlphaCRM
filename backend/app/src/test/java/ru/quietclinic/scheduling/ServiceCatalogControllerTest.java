package ru.quietclinic.scheduling;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ServiceCatalogControllerTest {
  @Autowired MockMvc mvc;

  @Test
  void listsServicesWithDurationAndPrice() throws Exception {
    mvc.perform(get("/api/v1/services").header("X-Demo-Role", "CASHIER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].name").value("Первичный приём"))
        .andExpect(jsonPath("$[0].durationMinutes").value(50))
        .andExpect(jsonPath("$[0].priceMinor").value(450000))
        .andExpect(jsonPath("$[1].name").value("Повторный приём"))
        .andExpect(jsonPath("$[1].durationMinutes").value(30))
        .andExpect(jsonPath("$[1].priceMinor").value(300000))
        .andExpect(jsonPath("$[1].currency").value("RUB"));
  }
}
