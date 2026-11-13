package ru.quietclinic.insights;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsOverviewServiceTest {
  @Test
  void mergesOverlappingIntervalsAndIgnoresEmptyOnes() {
    assertThat(
            AnalyticsOverviewService.unionMinutes(
                List.of(
                    new AnalyticsOverviewService.Interval(540, 600),
                    new AnalyticsOverviewService.Interval(570, 630),
                    new AnalyticsOverviewService.Interval(660, 690),
                    new AnalyticsOverviewService.Interval(700, 700))))
        .isEqualTo(120);
    assertThat(AnalyticsOverviewService.unionMinutes(List.of())).isZero();
  }
}
