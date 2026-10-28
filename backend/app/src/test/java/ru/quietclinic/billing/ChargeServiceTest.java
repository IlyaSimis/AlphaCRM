package ru.quietclinic.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

// Та же конфигурация, что у HTTP-тестов: Spring переиспользует контекст и базу H2, а не
// запускает Liquibase второй раз на той же базе в памяти.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ChargeServiceTest {
  // 502 — завершённый приём Петрова из seed, счёта по нему нет.
  static final UUID COMPLETED_APPOINTMENT = UUID.fromString("00000000-0000-0000-0000-000000000502");

  @Autowired ChargeService charges;
  @Autowired ChargeRepository repository;

  @BeforeEach
  void actAsDoctor() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "00000000-0000-0000-0000-000000000201",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void completedVisitGetsExactlyOnePendingCharge() {
    var first = charges.chargeForCompletedVisit(COMPLETED_APPOINTMENT, 300000, "RUB");
    var second = charges.chargeForCompletedVisit(COMPLETED_APPOINTMENT, 300000, "RUB");

    assertThat(second).isEqualTo(first);
    var charge = repository.findByAppointmentId(COMPLETED_APPOINTMENT).orElseThrow();
    assertThat(charge.status).isEqualTo("PENDING");
    assertThat(charge.amountMinor).isEqualTo(300000L);
    assertThat(charge.currency).isEqualTo("RUB");
  }
}
