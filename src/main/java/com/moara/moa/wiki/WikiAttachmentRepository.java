package com.moara.moa.wiki;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WikiAttachmentRepository extends JpaRepository<WikiAttachment, UUID> {
  List<WikiAttachment> findByTenantIdAndPageIdOrderByCreatedAtDesc(UUID tenantId, UUID pageId);

  Optional<WikiAttachment> findByIdAndTenantId(UUID id, UUID tenantId);

  /** 기관이 첨부로 쓰는 총 바이트. 첨부가 없으면 0(null 아님). */
  @Query("select coalesce(sum(a.sizeBytes), 0) from WikiAttachment a where a.tenantId = :tenantId")
  long sumSizeBytesByTenantId(@Param("tenantId") UUID tenantId);
}
