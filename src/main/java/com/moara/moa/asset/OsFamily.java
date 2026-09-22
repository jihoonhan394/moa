package com.moara.moa.asset;

/** 서버 OS 계열 — 카테고리와 별개의 필터·통계 축(윈도우/리눅스/기타). 상세 버전은 osType(자유 텍스트). */
public enum OsFamily {
  WINDOWS("윈도우"),
  LINUX("리눅스"),
  OTHER("기타");

  private final String label;

  OsFamily(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
