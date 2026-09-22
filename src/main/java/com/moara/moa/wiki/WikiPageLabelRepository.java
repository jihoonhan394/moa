package com.moara.moa.wiki;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiPageLabelRepository extends JpaRepository<WikiPageLabel, UUID> {
  List<WikiPageLabel> findAllByTenantIdAndPageIdOrderByLabelAsc(UUID tenantId, UUID pageId);

  Optional<WikiPageLabel> findByTenantIdAndId(UUID tenantId, UUID id);

  boolean existsByTenantIdAndPageIdAndLabel(UUID tenantId, UUID pageId, String label);
}
