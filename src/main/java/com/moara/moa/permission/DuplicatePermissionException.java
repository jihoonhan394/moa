package com.moara.moa.permission;

/** 같은 테넌트에 동일 이름 권한이 이미 존재. */
public class DuplicatePermissionException extends RuntimeException {
  public DuplicatePermissionException(String name) {
    super("같은 이름의 권한이 이미 있습니다: " + name);
  }
}
