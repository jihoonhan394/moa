package com.moara.moa.wiki;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiFavoriteRepository extends JpaRepository<WikiFavorite, UUID> {
  Optional<WikiFavorite> findByTenantIdAndUserIdAndPageId(UUID tenantId, UUID userId, UUID pageId);

  List<WikiFavorite> findAllByTenantIdAndUserId(UUID tenantId, UUID userId);

  boolean existsByTenantIdAndUserIdAndPageId(UUID tenantId, UUID userId, UUID pageId);
}
