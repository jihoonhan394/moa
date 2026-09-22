package com.moara.moa.permission;

import java.util.UUID;

/** 해당 테넌트에서 권한을 찾을 수 없음(타 테넌트 접근 포함). */
public class PermissionNotFoundException extends RuntimeException {
  public PermissionNotFoundException(UUID id) {
    super("권한을 찾을 수 없습니다: " + id);
  }
}
