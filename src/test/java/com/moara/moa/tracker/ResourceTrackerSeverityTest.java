package com.moara.moa.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 만기 심각도 임계값(EXPIRED/CRITICAL≤7/WARNING≤30/OK) 순수 단위 검증. */
class ResourceTrackerSeverityTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 8, 9);

  private ResourceTracker due(LocalDate dueOn) {
    return new ResourceTracker(
        UUID.randomUUID(), UUID.randomUUID(), TrackerTargetType.ASSET, UUID.randomUUID(),
        "cert", dueOn, null, "SSL_PROBE", OffsetDateTime.now());
  }

  @Test
  void classifiesByDaysRemaining() {
    assertThat(due(TODAY.minusDays(1)).severity(TODAY)).isEqualTo("EXPIRED");
    assertThat(due(TODAY).severity(TODAY)).isEqualTo("CRITICAL"); // 0일 남음
    assertThat(due(TODAY.plusDays(7)).severity(TODAY)).isEqualTo("CRITICAL");
    assertThat(due(TODAY.plusDays(8)).severity(TODAY)).isEqualTo("WARNING");
    assertThat(due(TODAY.plusDays(30)).severity(TODAY)).isEqualTo("WARNING");
    assertThat(due(TODAY.plusDays(31)).severity(TODAY)).isEqualTo("OK");
  }

  @Test
  void daysRemainingIsSignedFromToday() {
    assertThat(due(TODAY.plusDays(5)).daysRemaining(TODAY)).isEqualTo(5);
    assertThat(due(TODAY.minusDays(3)).daysRemaining(TODAY)).isEqualTo(-3);
  }
}
