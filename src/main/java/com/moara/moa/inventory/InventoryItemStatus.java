package com.moara.moa.inventory;

/** 인벤토리 항목 상태: 가용(재고), 배정됨(사용자에게), 폐기(불용). */
public enum InventoryItemStatus {
  AVAILABLE("가용"),
  ASSIGNED("배정됨"),
  RETIRED("폐기");

  private final String label;

  InventoryItemStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
