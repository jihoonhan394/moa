package com.moara.moa.permission;

/**
 * 권한 부여 단위 프로토콜. {@code DEFAULT}는 "자산 기본 프로토콜 접근"을 뜻하며,
 * NULL 대신 명시값으로 두어 UNIQUE 무결성을 보장한다(설계 D6).
 */
public enum PermissionProtocol {
  DEFAULT,
  SSH,
  RDP,
  HTTP,
  HTTPS,
  WEB
}
