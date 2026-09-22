package com.moara.moa.tenant;

/**
 * 기관(테넌트)이 켜고 끌 수 있는 기능 모듈. 코어 기능(대시보드/사용자·그룹·권한/감사)은 토글 대상이 아니다.
 * 기관별 보유 집합은 {@code tenant_features}에 저장되고, 메뉴 노출 + 라우트 접근을 함께 강제한다.
 */
public enum FeatureModule {
  ASSETS("자산 관리"),
  SERVER_ACCESS("서버 접속"),
  SOLUTIONS("솔루션 제어"),
  CREDENTIALS("자격증명 볼트"),
  INVENTORY("실물·SW 인벤토리"),
  RESERVATION("공유자산 예약"),
  WIKI("위키");

  private final String label;

  FeatureModule(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
