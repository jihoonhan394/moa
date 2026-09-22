package com.moara.moa.user;

public enum UserRole {
  SYSTEM_ADMIN("플랫폼 관리자"),
  TENANT_ADMIN("기관 관리자"),
  INFRA_MANAGER("인프라 관리자"),
  ASSET_MANAGER("자산 관리자"),
  USER("일반 사용자");

  private final String label;

  UserRole(String label) {
    this.label = label;
  }

  /** 화면 표시용 한글 라벨. */
  public String getLabel() {
    return label;
  }
}
