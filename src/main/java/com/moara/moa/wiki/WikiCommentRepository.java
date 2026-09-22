package com.moara.moa.wiki;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiCommentRepository extends JpaRepository<WikiComment, UUID> {
  List<WikiComment> findAllByTenantIdAndPageIdOrderByCreatedAtAsc(UUID tenantId, UUID pageId);

  Optional<WikiComment> findByTenantIdAndId(UUID tenantId, UUID id);
}
