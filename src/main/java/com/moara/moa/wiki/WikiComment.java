package com.moara.moa.wiki;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 위키 문서 댓글. 내용은 평문(화면에서 이스케이프 표시). */
@Entity
@Table(name = "wiki_comments")
public class WikiComment {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "page_id", nullable = false)
  private UUID pageId;

  @Column(name = "author_user_id")
  private UUID authorUserId;

  private String content;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  protected WikiComment() {}

  public WikiComment(UUID id, UUID tenantId, UUID pageId, UUID authorUserId, String content, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.pageId = pageId;
    this.authorUserId = authorUserId;
    this.content = content;
    this.createdAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getPageId() { return pageId; }
  public UUID getAuthorUserId() { return authorUserId; }
  public String getContent() { return content; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
