package com.moara.moa.wiki;

/** 위키 공간 접근 수준. 순서대로 강함: 열람 &lt; 편집 &lt; 관리(부서장). */
public enum WikiAccessLevel {
  VIEW("열람"),
  EDIT("편집"),
  MANAGE("관리");

  private final String label;

  WikiAccessLevel(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  /** this가 other 이상인지(예: MANAGE.atLeast(EDIT)=true). */
  public boolean atLeast(WikiAccessLevel other) {
    return this.ordinal() >= other.ordinal();
  }
}
