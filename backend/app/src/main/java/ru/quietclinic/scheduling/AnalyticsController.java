package ru.quietclinic.scheduling;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
  private final AppointmentStatsService stats;

  AnalyticsController(AppointmentStatsService stats) {
    this.stats = stats;
  }

  @GetMapping("/appointments")
  AppointmentStatsView appointments(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) UUID practitionerId) {
    return stats.stats(from, to, practitionerId);
  }
}
