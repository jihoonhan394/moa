package com.moara.moa.solution;

/**
 * 헬스 체크 결과 상태.
 *
 * <p><b>UNKNOWN 과 DOWN 을 섞지 않는 것이 이 enum 의 요점이다.</b> 포트를 안 적었거나 제어
 * 자격증명이 없어서 못 재 본 것은 장애가 아니다. 그것까지 DOWN 으로 알리면 담당자는 설정이 덜 된
 * 솔루션 때문에 새벽에 깨고, 다음부터 이 알림을 끈다. 그러면 진짜 장애도 같이 묻힌다.
 */
public enum HealthState {
  /** 아직 못 재 봤다 — 점검 이력이 없거나, 설정이 모자라 점검 자체가 불가능하다. 알리지 않는다. */
  UNKNOWN("확인 안 됨"),

  /** 살아 있다. */
  UP("정상"),

  /** 연속 실패 기준을 넘겼다. 이때만 알린다. */
  DOWN("장애");

  private final String label;

  HealthState(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
