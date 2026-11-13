package ru.quietclinic.insights;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Управленческая аналитика; собственные приёмы врача остаются в /analytics/appointments. */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsOverviewController {
  private final AnalyticsOverviewService analytics;
  private final ServiceAnalyticsService services;
  private final AcquisitionAnalyticsService acquisition;

  AnalyticsOverviewController(
      AnalyticsOverviewService analytics,
      ServiceAnalyticsService services,
      AcquisitionAnalyticsService acquisition) {
    this.analytics = analytics;
    this.services = services;
    this.acquisition = acquisition;
  }

  @GetMapping("/acquisition")
  AcquisitionAnalyticsView acquisition(
      @RequestParam LocalDate from, @RequestParam LocalDate to) {
    return acquisition.report(from, to);
  }

  @GetMapping("/overview")
  AnalyticsOverviewView overview(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) UUID practitionerId) {
    return analytics.overview(from, to, practitionerId);
  }

  @GetMapping("/services")
  ServiceAnalyticsView services(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(required = false) UUID practitionerId) {
    return services.report(from, to, practitionerId);
  }

  @GetMapping("/services/appointments")
  ServiceAnalyticsView.Details serviceAppointments(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam String serviceKey,
      @RequestParam(required = false) UUID practitionerId) {
    return services.details(from, to, serviceKey, practitionerId);
  }
}
