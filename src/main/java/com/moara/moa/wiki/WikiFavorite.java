package com.moara.moa.wiki;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 위키 즐겨찾기(사용자별 북마크). */
@Entity
@Table(name = "wiki_favorites")
public class WikiFavorite {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "page_id", nullable = false)
  private UUID pageId;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  protected WikiFavorite() {}

  public WikiFavorite(UUID id, UUID tenantId, UUID userId, UUID pageId, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.userId = userId;
    this.pageId = pageId;
    this.createdAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getUserId() { return userId; }
  public UUID getPageId() { return pageId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
