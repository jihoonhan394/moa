package com.moara.moa.reservation;

/** 예약 상태: 예약됨 / 취소됨. */
public enum ReservationStatus {
  BOOKED("예약됨"),
  CANCELLED("취소됨");

  private final String label;

  ReservationStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
