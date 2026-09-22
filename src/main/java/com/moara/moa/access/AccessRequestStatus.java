package com.moara.moa.access;

/**
 * 접근요청 상태. PENDING(승인 대기) → APPROVED/REJECTED(검토 결과) 또는 CANCELED(요청자 취소).
 * APPROVED → EXPIRED(기간 만료·자동 반납) 또는 REVOKED(관리자 강제 회수).
 */
public enum AccessRequestStatus {
  PENDING("승인 대기"),
  APPROVED("승인됨"),
  REJECTED("반려됨"),
  CANCELED("요청 취소"),
  REVOKED("회수됨"),
  EXPIRED("기간 만료");

  private final String label;

  AccessRequestStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
