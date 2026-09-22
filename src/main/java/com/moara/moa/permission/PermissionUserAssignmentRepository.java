package com.moara.moa.permission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionUserAssignmentRepository
    extends JpaRepository<PermissionUserAssignment, UUID> {
  List<PermissionUserAssignment> findAllByTenantIdAndPermissionId(UUID tenantId, UUID permissionId);

  List<PermissionUserAssignment> findAllByTenantIdAndUserId(UUID tenantId, UUID userId);

  Optional<PermissionUserAssignment> findByTenantIdAndPermissionIdAndUserId(
      UUID tenantId, UUID permissionId, UUID userId);

  /** 퇴사 회수: 이 사용자의 모든 직접 권한 배정 삭제. 삭제 건수 반환. */
  long deleteByTenantIdAndUserId(UUID tenantId, UUID userId);
}
