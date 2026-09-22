package com.moara.moa.wiki;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiSpaceRepository extends JpaRepository<WikiSpace, UUID> {
  List<WikiSpace> findAllByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<WikiSpace> findByTenantIdAndId(UUID tenantId, UUID id);
}
