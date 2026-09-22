package com.moara.moa.group;

/**
 * 사용자와 그룹이 서로 다른 테넌트에 속할 때 매핑 시도를 차단한다.
 */
public class CrossTenantMembershipException extends RuntimeException {
  public CrossTenantMembershipException(String message) {
    super(message);
  }
}
