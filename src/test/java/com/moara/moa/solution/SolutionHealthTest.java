package com.moara.moa.solution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 상태 전이만 따로 본다. 스프링을 띄우지 않으므로 경계(연속 실패 1회 vs 2회, 중복 알림)를
 * 빠르게 다 짚을 수 있다 — 이 판단이 틀리면 알림이 안 가거나 분마다 간다.
 */
class SolutionHealthTest {

  private static final int THRESHOLD = 2;
  private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-06T09:00:00+09:00");

  private SolutionHealth fresh() {
    return SolutionHealth.initial(UUID.randomUUID(), UUID.randomUUID());
  }

  @Test
  @DisplayName("처음은 UNKNOWN 이고 알리지 않는다")
  void 처음은_알리지_않는다() {
    SolutionHealth health = fresh();
    assertThat(health.getState()).isEqualTo(HealthState.UNKNOWN);
    assertThat(health.needsAlert()).isFalse();
  }

  @Test
  @DisplayName("⚠️ 1회 실패로는 DOWN 이 아니다 — 재기동 중 한 번에 전원이 알림을 받으면 안 된다")
  void 한_번_실패로는_장애가_아니다() {
    SolutionHealth health = fresh();
    health.recordSuccess(T0);

    health.recordFailure("포트 닫힘", T0.plusMinutes(1), THRESHOLD);

    assertThat(health.getState()).isEqualTo(HealthState.UP);
    assertThat(health.getConsecutiveFailures()).isEqualTo(1);
    assertThat(health.needsAlert()).isFalse();
  }

  @Test
  @DisplayName("연속 2회 실패에서 DOWN 이 되고 그때 알린다")
  void 두_번_연속이면_장애() {
    SolutionHealth health = fresh();
    health.recordSuccess(T0);
    health.recordFailure("포트 닫힘", T0.plusMinutes(1), THRESHOLD);
    health.recordFailure("포트 닫힘", T0.plusMinutes(2), THRESHOLD);

    assertThat(health.getState()).isEqualTo(HealthState.DOWN);
    assertThat(health.getChangedAt()).isEqualTo(T0.plusMinutes(2));
    assertThat(health.needsAlert()).isTrue();
  }

  @Test
  @DisplayName("⚠️ DOWN 이 지속되는 동안 다시 알리지 않는다 — 안 막으면 1분마다 전원에게 간다")
  void 장애_지속_중에는_다시_알리지_않는다() {
    SolutionHealth health = downAndAlerted();

    for (int minute = 3; minute < 10; minute++) {
      health.recordFailure("포트 닫힘", T0.plusMinutes(minute), THRESHOLD);
      assertThat(health.needsAlert()).as("%d분째", minute).isFalse();
    }
  }

  @Test
  @DisplayName("한 번만 성공해도 복구다 — 복구는 즉시 알린다")
  void 복구는_즉시_알린다() {
    SolutionHealth health = downAndAlerted();

    health.recordSuccess(T0.plusMinutes(5));

    assertThat(health.getState()).isEqualTo(HealthState.UP);
    assertThat(health.getConsecutiveFailures()).isZero();
    assertThat(health.getDetail()).isNull();
    assertThat(health.needsAlert()).isTrue();
  }

  @Test
  @DisplayName("장애를 겪지 않은 솔루션의 첫 UP 은 알리지 않는다 — 아무 일도 일어나지 않았다")
  void 처음_정상은_알리지_않는다() {
    SolutionHealth health = fresh();
    health.recordSuccess(T0);
    assertThat(health.needsAlert()).isFalse();
  }

  @Test
  @DisplayName("⚠️ UNKNOWN(설정 공백)은 알리지 않는다 — 장애가 아니다")
  void 설정_공백은_알리지_않는다() {
    SolutionHealth health = fresh();
    health.recordUnknown("제어 자격증명이 설정되지 않아 점검할 수 없습니다.", T0);

    assertThat(health.getState()).isEqualTo(HealthState.UNKNOWN);
    assertThat(health.needsAlert()).isFalse();
    // 재 보지 않았으므로 "N분 전 점검"이라고 쓸 수 없다.
    assertThat(health.getCheckedAt()).isNull();
  }

  @Test
  @DisplayName("설정 공백은 연속 실패로 세지 않는다 — 고친 즉시 정상 판정이 되어야 한다")
  void 설정_공백은_실패로_세지_않는다() {
    SolutionHealth health = fresh();
    health.recordFailure("포트 닫힘", T0, THRESHOLD);
    assertThat(health.getConsecutiveFailures()).isEqualTo(1);

    health.recordUnknown("점검할 포트가 없습니다.", T0.plusMinutes(1));

    assertThat(health.getConsecutiveFailures()).isZero();
  }

  @Test
  @DisplayName("장애 중 설정이 빠졌다가 돌아오면, 복구 알림이 사라지지 않는다")
  void 장애_중_설정이_빠져도_복구_알림은_남는다() {
    SolutionHealth health = downAndAlerted();

    // 관리자가 점검 대상을 지웠다 → 못 재는 상태
    health.recordUnknown("점검할 포트가 없습니다.", T0.plusMinutes(5));
    assertThat(health.needsAlert()).isFalse();

    // 다시 적고 서비스도 살아났다 → "복구됐다"를 받아야 한다
    health.recordSuccess(T0.plusMinutes(10));
    assertThat(health.needsAlert()).isTrue();
  }

  @Test
  @DisplayName("사유는 500자로 자른다 — detail 컬럼이 VARCHAR(500) 이다")
  void 사유는_컬럼_길이에서_자른다() {
    SolutionHealth health = fresh();
    health.recordFailure("가".repeat(900), T0, THRESHOLD);
    assertThat(health.getDetail()).hasSize(500);
  }

  /** DOWN 이 되어 알림까지 보낸 상태. */
  private SolutionHealth downAndAlerted() {
    SolutionHealth health = fresh();
    health.recordSuccess(T0);
    health.recordFailure("포트 닫힘", T0.plusMinutes(1), THRESHOLD);
    health.recordFailure("포트 닫힘", T0.plusMinutes(2), THRESHOLD);
    health.markAlerted();
    return health;
  }
}
