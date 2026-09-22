package com.moara.moa.permission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionGroupAssignmentRepository
    extends JpaRepository<PermissionGroupAssignment, UUID> {
  List<PermissionGroupAssignment> findAllByTenantIdAndPermissionId(UUID tenantId, UUID permissionId);

  List<PermissionGroupAssignment> findAllByTenantIdAndGroupId(UUID tenantId, UUID groupId);

  Optional<PermissionGroupAssignment> findByTenantIdAndPermissionIdAndGroupId(
      UUID tenantId, UUID permissionId, UUID groupId);
}
