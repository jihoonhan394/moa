package com.moara.moa.user;

public enum UserStatus {
  /** 가입 신청 후 관리자 승인 대기. 로그인 불가(활성 전까지). */
  PENDING("승인 대기"),
  /** 정상 재직. 로그인 가능한 유일한 상태. */
  ACTIVE("활성"),
  /** 일시 정지(휴직·보안 보류 등). 접근 권한은 보존되며 재활성 시 그대로 복원. 로그인 불가. */
  DISABLED("비활성"),
  /** 퇴사(종료 상태). 모든 접근 권한이 회수되며 되돌리지 않는다. 로그인 불가. */
  OFFBOARDED("퇴사");

  private final String label;

  UserStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
