package com.moara.moa.inventory;

/**
 * 자산 보관자의 종류. <b>"지금 이 물건을 누가 갖고 있나"의 답은 사람만이 아니다</b> — 창고일 수도,
 * 고객처일 수도, 그리고 다른 장비일 수도 있다.
 *
 * <p>마지막 하나가 이 설계의 열쇠다. 부품 장착을 "상위 장비에게 보관을 넘긴 것"으로 보면,
 * 납품·반입과 부품 이동이 별도 테이블 없이 같은 장부에 들어온다.
 */
public enum InventoryHolderType {
  WAREHOUSE("창고", false),
  USER("사용자", true),
  GROUP("부서", true),
  CUSTOMER("고객처", false),
  VENDOR("업체", false),
  PARENT_ITEM("장착됨", true),
  DISPOSED("폐기", false);

  private final String label;
  /** 내부 식별자(holder_id)로 가리키는 대상인지. 외부 거래처는 이름만 남긴다. */
  private final boolean internalTarget;

  InventoryHolderType(String label, boolean internalTarget) {
    this.label = label;
    this.internalTarget = internalTarget;
  }

  public String getLabel() {
    return label;
  }

  public boolean isInternalTarget() {
    return internalTarget;
  }

  /** 사내에 물건이 있는 상태인지. 아니면 밖(고객처·업체)에 나가 있거나 폐기됐다. */
  public boolean isInHouse() {
    return this == WAREHOUSE || this == USER || this == GROUP || this == PARENT_ITEM;
  }
}
