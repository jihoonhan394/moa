package com.moara.moa.inventory;

/** 인벤토리 항목 상태: 가용(재고), 배정됨, 반납 대기(퇴사 후 실물 미확인), 폐기(불용). */
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
  RETIRED("폐기");

  private final String label;

  InventoryItemStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
