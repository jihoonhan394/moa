package com.moara.moa.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * DB에서 읽은 시각은 한국 시각(+09:00)이어야 한다. 화면은 받은 값을 그대로 포맷하므로,
 * UTC로 읽히면 모든 화면의 시각이 9시간 이르게 보인다.
 */
@SpringBootTest
@ActiveProfiles("test")
class TimestampZoneTest {
  @Autowired private TenantService tenantService;
  @Autowired private TenantRepository tenantRepository;

  @Test
  void timestampsAreReadInKoreanTime() {
    OffsetDateTime before = OffsetDateTime.now();
    Tenant created = tenantService.createTenant(new CreateTenantCommand("시각사", "TZ" + System.nanoTime()));

    OffsetDateTime loaded = tenantRepository.findById(created.getId()).orElseThrow().getCreatedAt();

    assertThat(loaded.getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    // 순간은 그대로다(저장할 때 바뀌지 않았다).
    assertThat(loaded).isCloseTo(before, org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
  }
}
