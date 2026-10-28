package ru.quietclinic.billing;

import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Отчёты финансов: продажи и выручка за период. */
@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {
  private final FinanceService finance;

  FinanceController(FinanceService finance) {
    this.finance = finance;
  }

  /** Продажи по дням клиники, услугам или врачам: from и to включительно (YYYY-MM-DD). */
  @GetMapping("/sales")
  SalesReport sales(
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(defaultValue = "day") String groupBy) {
    return finance.sales(from, to, groupBy);
  }
}
