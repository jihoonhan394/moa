package com.moara.moa.category;

/** 카테고리가 속한 도메인. 자산(실물·SW 인벤토리)과 공유자산(예약)이 각자 목록을 갖는다. */
public enum CategoryDomain {
  ASSET("자산"),
  SHARED_RESOURCE("공유자산"),
  SERVER("서버"),
  SOLUTION("솔루션");

  private final String label;

  CategoryDomain(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
