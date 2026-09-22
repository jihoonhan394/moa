package com.moara.moa.wiki;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiPageRevisionRepository extends JpaRepository<WikiPageRevision, UUID> {
  List<WikiPageRevision> findAllByTenantIdAndPageIdOrderByCreatedAtDesc(UUID tenantId, UUID pageId);

  Optional<WikiPageRevision> findByTenantIdAndId(UUID tenantId, UUID id);
}
