package com.moara.moa.tracker;

/** 만기·점검 항목이 붙는 자원 종류. INVENTORY=실물/SW 인벤토리, ASSET=서버/접속 자산(SSL 등). */
public enum TrackerTargetType {
  INVENTORY("실물/SW 자산"),
  ASSET("서버/접속 자산");

  private final String label;

  TrackerTargetType(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
