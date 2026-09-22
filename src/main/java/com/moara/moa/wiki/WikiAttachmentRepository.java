package com.moara.moa.wiki;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiAttachmentRepository extends JpaRepository<WikiAttachment, UUID> {
  List<WikiAttachment> findByTenantIdAndPageIdOrderByCreatedAtDesc(UUID tenantId, UUID pageId);

  Optional<WikiAttachment> findByIdAndTenantId(UUID id, UUID tenantId);
}
