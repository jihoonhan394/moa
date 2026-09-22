package com.moara.moa.permission;

import com.moara.moa.user.OffboardHandler;
import com.moara.moa.user.OffboardOutcome;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 퇴사 회수: 사용자에게 직접 부여된 접근 권한 삭제(그룹 권한은 그룹 탈퇴로 전이 회수). */
@Component
public class PermissionOffboardHandler implements OffboardHandler {
  private final PermissionUserAssignmentRepository repository;

  public PermissionOffboardHandler(PermissionUserAssignmentRepository repository) {
    this.repository = repository;
  }

  @Override
  public OffboardOutcome offboard(UUID tenantId, UUID userId) {
    return new OffboardOutcome("직접 권한", repository.deleteByTenantIdAndUserId(tenantId, userId));
  }
}
