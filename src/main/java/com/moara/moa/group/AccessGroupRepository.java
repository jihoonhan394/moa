package com.moara.moa.group;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessGroupRepository extends JpaRepository<AccessGroup, UUID> {
  List<AccessGroup> findAllByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<AccessGroup> findByIdAndTenantId(UUID id, UUID tenantId);

  boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

  boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);
}
