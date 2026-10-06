package com.moara.moa.solution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 솔루션 하나의 현재 헬스 상태. 설정({@link ManagedSolution})과 분리된 행이다 — 이유는
 * {@code V76__create_solution_health.sql} 주석에 있다.
 *
 * <p><b>상태 전이 판단을 엔티티가 갖는다.</b> "몇 번 연속 실패하면 DOWN 인가", "언제 changed_at
 * 을 미는가"가 서비스에 흩어지면 알림 중복·누락이 조용히 생긴다. 여기 모아 두면 단위 테스트로
 * 전이만 따로 검증할 수 있다.
 */
@Entity
@Table(name = "solution_health")
public class SolutionHealth {

  @Id
  @Column(name = "solution_id")
  private UUID solutionId;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private HealthState state;

  @Column(name = "consecutive_failures", nullable = false)
  private int consecutiveFailures;

  @Column(name = "checked_at")
  private OffsetDateTime checkedAt;

  @Column(name = "changed_at")
  private OffsetDateTime changedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "alerted_state", length = 20)
  private HealthState alertedState;

  @Column(length = 500)
  private String detail;

  protected SolutionHealth() {}

  private SolutionHealth(UUID solutionId, UUID tenantId) {
    this.solutionId = solutionId;
    this.tenantId = tenantId;
    this.state = HealthState.UNKNOWN;
    this.consecutiveFailures = 0;
  }

  /** 점검 이력이 없는 솔루션의 첫 행. */
  public static SolutionHealth initial(UUID solutionId, UUID tenantId) {
    return new SolutionHealth(solutionId, tenantId);
  }

  /** 점검 성공. 연속 실패 수를 즉시 0으로 돌린다 — 복구는 한 번만 성공해도 복구다. */
  public void recordSuccess(OffsetDateTime now) {
    this.consecutiveFailures = 0;
    this.detail = null;
    if (this.state != HealthState.UP) {
      this.state = HealthState.UP;
      this.changedAt = now;
    }
    this.checkedAt = now;
  }

  /**
   * 점검 실패. <b>{@code threshold} 회 연속으로 실패해야 DOWN 이다.</b>
   *
   * <p>1회로 판정하면 재기동 중 한 번, 네트워크가 순간 끊긴 한 번에 전원이 알림을 받는다.
   * 그런 알림이 몇 번 반복되면 사람들은 알림을 끄고, 그때부터 이 기능은 없는 것과 같다.
   *
   * <p>기준에 못 미치는 동안은 이전 상태를 유지한다(UP 이면 UP). 화면에 "흔들리는 중"을
   * 따로 보여 주지 않는 것은, 그 상태를 보고 할 일이 없기 때문이다.
   */
  public void recordFailure(String detail, OffsetDateTime now, int threshold) {
    this.consecutiveFailures++;
    this.detail = trim(detail);
    if (this.consecutiveFailures >= threshold && this.state != HealthState.DOWN) {
      this.state = HealthState.DOWN;
      this.changedAt = now;
    }
    this.checkedAt = now;
  }

  /**
   * 점검을 못 했다(설정이 모자라다). 장애가 아니므로 연속 실패 수를 세지 않는다 — 설정을
   * 고치는 즉시 정상 판정이 되어야 한다.
   *
   * <p>{@code checkedAt} 과 {@code alertedState} 는 건드리지 않는다. 재 보지 않았으므로
   * "N분 전 점검"이라고 쓰면 거짓이고, 알림 이력을 지우면 장애 중 설정이 빠졌을 때 나중에
   * 복구 알림이 사라진다.
   */
  public void recordUnknown(String detail, OffsetDateTime now) {
    this.consecutiveFailures = 0;
    this.detail = trim(detail);
    if (this.state != HealthState.UNKNOWN) {
      this.state = HealthState.UNKNOWN;
      this.changedAt = now;
    }
  }

  /** 지금 상태를 알렸다고 표시한다. 같은 상태로는 다시 알리지 않는다. */
  public void markAlerted() {
    this.alertedState = this.state;
  }

  /**
   * 이 상태를 알려야 하나.
   *
   * <p>UNKNOWN 은 알리지 않는다. 장애 발생(→DOWN)과 복구(DOWN→UP)만 알림 가치가 있다.
   * 한 번도 장애가 아니었던 솔루션의 첫 UP 은 알리지 않는다 — 아무 일도 일어나지 않았다.
   */
  public boolean needsAlert() {
    if (state == HealthState.UNKNOWN || state == alertedState) {
      return false;
    }
    return state == HealthState.DOWN || alertedState == HealthState.DOWN;
  }

  private static String trim(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
  }

  public UUID getSolutionId() { return solutionId; }
  public UUID getTenantId() { return tenantId; }
  public HealthState getState() { return state; }
  public int getConsecutiveFailures() { return consecutiveFailures; }
  public OffsetDateTime getCheckedAt() { return checkedAt; }
  public OffsetDateTime getChangedAt() { return changedAt; }
  public HealthState getAlertedState() { return alertedState; }
  public String getDetail() { return detail; }
}
