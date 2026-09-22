package com.moara.moa.permission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
  List<Permission> findAllByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<Permission> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<Permission> findByTenantIdAndName(UUID tenantId, String name);
}
