package ru.quietclinic.insights;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Персональный дашборд — первая страница после входа (сценарий D9). */
@RestController
@RequestMapping("/api/v1/me")
public class DashboardController {
  private final DashboardService dashboards;

  DashboardController(DashboardService dashboards) {
    this.dashboards = dashboards;
  }

  @GetMapping("/dashboard")
  DashboardView dashboard() {
    return dashboards.dashboard();
  }
}
