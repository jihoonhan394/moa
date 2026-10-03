package com.moara.moa.inventory;

import com.moara.moa.category.CategoryService;
import java.util.regex.Pattern;

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

  /**
   * 카테고리 경로의 최상위(실물/SW)로 유형을 파생한다. 알 수 없으면 {@code null}.
   *
   * <p>PC 등록 화면과 모바일 등록 화면이 같은 규칙을 써야 한다. 한쪽에만 두면 두 화면이
   * 다른 유형을 만들어 내고, 그 차이는 집계에서야 드러난다.
   */
  public static InventoryItemType fromCategoryPath(String categoryPath) {
    if (categoryPath == null || categoryPath.isBlank()) {
      return null;
    }
    String root = categoryPath.split(Pattern.quote(CategoryService.PATH_SEP))[0].trim();
    for (InventoryItemType type : values()) {
      if (type.getLabel().equals(root)) {
        return type;
      }
    }
    return null;
  }
}
