package com.moara.moa.consumable;

/**
 * 소모품 요청 상태.
 *
 * <p>"확인"을 <b>접수</b>와 <b>완료</b>로 나눈 이유: 그냥 "확인"이면 봤다는 건지, 주문하겠다는
 * 건지, 이미 줬다는 건지 알 수 없다. 접수는 "주문할게요", 완료는 "주문했어요"다.
 */
public enum ConsumableRequestStatus {
  REQUESTED("요청됨"),
  ACKNOWLEDGED("접수됨"),
  HELD("보류"),
  REJECTED("거절"),
  FULFILLED("완료");

  private final String label;

  ConsumableRequestStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  /** 담당자가 아직 처리하지 않은 상태. 목록 배지·알림 묶음의 기준이다. */
  public boolean isOpen() {
    return this == REQUESTED || this == ACKNOWLEDGED || this == HELD;
  }
}
