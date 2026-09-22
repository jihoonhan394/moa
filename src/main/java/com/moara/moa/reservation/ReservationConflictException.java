package com.moara.moa.reservation;

/** 예약 불가(시간대 중복 또는 잘못된 시간). 컨트롤러가 사용자 메시지로 변환한다. */
public class ReservationConflictException extends RuntimeException {
  public ReservationConflictException(String message) {
    super(message);
  }
}
