package com.moara.moa.maintenance;

/** 점검/담당 대상 유형. */
public enum MaintenanceTargetType {
  ASSET("서버/자산"),
  SOLUTION("솔루션");

  private final String label;

  MaintenanceTargetType(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }
}
