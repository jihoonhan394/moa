package com.moara.moa.wiki;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiTemplateRepository extends JpaRepository<WikiTemplate, UUID> {
  List<WikiTemplate> findAllByTenantIdAndSpaceIdOrderByNameAsc(UUID tenantId, UUID spaceId);

  Optional<WikiTemplate> findByTenantIdAndId(UUID tenantId, UUID id);
}
