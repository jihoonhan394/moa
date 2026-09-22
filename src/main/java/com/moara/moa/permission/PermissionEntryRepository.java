package com.moara.moa.permission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionEntryRepository extends JpaRepository<PermissionEntry, UUID> {
  List<PermissionEntry> findAllByTenantIdAndPermissionId(UUID tenantId, UUID permissionId);

  Optional<PermissionEntry> findByTenantIdAndPermissionIdAndAssetIdAndAction(
      UUID tenantId, UUID permissionId, UUID assetId, PermissionProtocol action);
}
