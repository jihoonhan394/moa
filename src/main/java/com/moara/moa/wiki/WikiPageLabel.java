package com.moara.moa.wiki;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 위키 문서 라벨(분류 태그). */
@Entity
@Table(name = "wiki_page_labels")
public class WikiPageLabel {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "page_id", nullable = false)
  private UUID pageId;

  private String label;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  protected WikiPageLabel() {}

  public WikiPageLabel(UUID id, UUID tenantId, UUID pageId, String label, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.pageId = pageId;
    this.label = label;
    this.createdAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getPageId() { return pageId; }
  public String getLabel() { return label; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
