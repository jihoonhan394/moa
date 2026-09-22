package com.moara.moa.wiki;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WikiPageRepository extends JpaRepository<WikiPage, UUID> {
  List<WikiPage> findAllByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

  List<WikiPage> findAllByTenantIdAndSpaceIdOrderByUpdatedAtDesc(UUID tenantId, UUID spaceId);

  Optional<WikiPage> findByTenantIdAndId(UUID tenantId, UUID id);

  /** 제목·본문 부분일치 검색(대소문자 무시). 권한 필터는 서비스/컨트롤러가 적용. */
  @Query("SELECT p FROM WikiPage p WHERE p.tenantId = :tenantId AND "
      + "(LOWER(p.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.content) LIKE LOWER(CONCAT('%', :q, '%'))) "
      + "ORDER BY p.updatedAt DESC")
  List<WikiPage> search(@Param("tenantId") UUID tenantId, @Param("q") String q);
}
