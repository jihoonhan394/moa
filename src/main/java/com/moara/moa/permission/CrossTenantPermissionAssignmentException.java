package com.moara.moa.permission;

import java.util.UUID;

/** 다른 테넌트(또는 테넌트 없는 SYSTEM_ADMIN) 사용자에게 권한을 부착하려 할 때. */
public class CrossTenantPermissionAssignmentException extends RuntimeException {
  public CrossTenantPermissionAssignmentException(UUID userId, UUID tenantId) {
    super("사용자 " + userId + " 는 테넌트 " + tenantId + " 에 속하지 않아 권한을 부착할 수 없습니다.");
  }
}
