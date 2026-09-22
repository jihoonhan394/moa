package com.moara.moa.inventory;

/** 인벤토리 항목 유형: 실물(노트북·모니터 등) 또는 SW(라이선스·구독). */
public enum InventoryItemType {
  PHYSICAL("실물"),
  SOFTWARE("SW");

  private final String label;

  InventoryItemType(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
