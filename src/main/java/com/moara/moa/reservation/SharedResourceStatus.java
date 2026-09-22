package com.moara.moa.reservation;

/** 공유자산 상태: 사용가능 / 사용중지(예약 불가). */
public enum SharedResourceStatus {
  ACTIVE("사용가능"),
  DISABLED("사용중지");

  private final String label;

  SharedResourceStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
