package com.moara.moa.wiki;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 위키 문서의 과거 버전 스냅샷. 수정 직전 상태를 담는다. */
@Entity
@Table(name = "wiki_page_revisions")
public class WikiPageRevision {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "page_id", nullable = false)
  private UUID pageId;

  private String title;

  @Column(columnDefinition = "text")
  private String content;

  @Column(name = "edited_by_user_id")
  private UUID editedByUserId;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  protected WikiPageRevision() {}

  public WikiPageRevision(
      UUID id, UUID tenantId, UUID pageId, String title, String content, UUID editedByUserId,
      OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.pageId = pageId;
    this.title = title;
    this.content = content;
    this.editedByUserId = editedByUserId;
    this.createdAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getPageId() { return pageId; }
  public String getTitle() { return title; }
  public String getContent() { return content; }
  public UUID getEditedByUserId() { return editedByUserId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
