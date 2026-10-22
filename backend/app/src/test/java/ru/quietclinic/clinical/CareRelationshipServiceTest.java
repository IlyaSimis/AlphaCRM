package ru.quietclinic.clinical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CareRelationshipServiceTest {
  static final UUID PETROV = UUID.fromString("00000000-0000-0000-0000-000000000201");

  @Autowired MockMvc mvc;
  @Autowired CareRelationshipService service;
  @Autowired CareRelationshipRepository relationships;

  private UUID newPatient() throws Exception {
    var body =
        mvc.perform(
                post("/api/v1/patients")
                    .header("X-Demo-Role", "ADMINISTRATOR")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"fullName":"Связь Лечения","birthDate":"1980-01-01","phone":"+70000000001",
                         "consentVersion":"v1.2","consentBasis":"SIGNED_FORM"}
                        """))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(body, "$.id"));
  }

  private long count(UUID patientId) {
    return relationships.findAll().stream()
        .filter(r -> r.practitionerId.equals(PETROV) && r.patientId.equals(patientId))
        .count();
  }

  @Test
  void createsOneActiveRelationshipForAYearAndIsIdempotent() throws Exception {
    var patient = newPatient();
    assertThat(service.isActive(PETROV, patient)).isFalse();

    service.ensureCareRelationship(PETROV, patient);
    service.ensureCareRelationship(PETROV, patient);

    assertThat(count(patient)).isEqualTo(1);
    assertThat(service.isActive(PETROV, patient)).isTrue();
    var relationship =
        relationships.findAll().stream().filter(r -> r.patientId.equals(patient)).findFirst();
    assertThat(relationship).isPresent();
    assertThat(Duration.between(relationship.get().validFrom, relationship.get().validUntil))
        .isEqualTo(CareRelationshipService.DEFAULT_TERM);
    assertThat(relationship.get().reason).isEqualTo("Назначение по записи");
  }
}
