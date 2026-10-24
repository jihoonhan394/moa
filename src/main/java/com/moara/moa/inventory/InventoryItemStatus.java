package com.moara.moa.inventory;

/**
 * 인벤토리 항목 상태: 가용(재고), 배정됨, 반납 대기(퇴사 후 실물 미확인), 회수 확인 대기(실물은
 * 받았고 관리자 처리 전), 폐기(불용).
 *
 * <p>퇴사 회수는 두 단계를 거쳐야 끝난다: 실물을 받은 사람이 <b>회수 완료</b>를 표시하고
 * ({@link #RETURN_PENDING} → {@link #RETURNED}), 자산 관리자가 <b>회수 완료 처리</b>를 해야
 * ({@link #RETURNED} → {@link #AVAILABLE}) 창고 입고가 기록되고 다시 배정할 수 있다.
 */
public enum InventoryItemStatus {
  AVAILABLE("가용"),
  ASSIGNED("배정됨"),
  /**
   * 반납해야 하는데 <b>실물을 아직 확인하지 못한</b> 상태. 퇴사 처리가 만든다.
   *
   * <p>가용과 구별하는 이유는 배정 때문이다. 확인 전에 가용으로 두면 다음 사람에게 배정되고,
   * 받으러 간 사람은 아무것도 찾지 못한다. 장부가 "창고에 있음"이라고 말하고 있으므로 그가
   * 잘못 찾았다고 여기게 된다.
   */
  RETURN_PENDING("반납 대기"),
  /**
   * 실물은 돌아왔지만 <b>자산 관리자가 아직 회수 완료 처리를 하지 않은</b> 상태.
   *
   * <p>받은 사람(부서장 등)의 표시만으로 끝내지 않는 이유: 퇴사는 정산이 걸린 순간이라 "받았다"는
   * 한쪽 주장을 장부를 책임지는 사람이 한 번 더 확인해야 한다. 확인 전에는 배정할 수 없다.
   */
  RETURNED("회수 확인 대기"),
  RETIRED("폐기");

  private final String label;

  InventoryItemStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
