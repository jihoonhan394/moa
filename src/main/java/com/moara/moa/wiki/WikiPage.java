package com.moara.moa.wiki;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 위키 문서. 내용은 평문 저장(화면에서 이스케이프 표시). 작성자·최종수정자를 추적한다. */
@Entity
@Table(name = "wiki_pages")
public class WikiPage {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "space_id")
  private UUID spaceId;

  private String title;

  @Column(columnDefinition = "text")
  private String content;

  @Column(name = "author_user_id")
  private UUID authorUserId;

  @Column(name = "updated_by_user_id")
  private UUID updatedByUserId;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  protected WikiPage() {}

  public WikiPage(UUID id, UUID tenantId, UUID spaceId, UUID authorUserId, WikiPageForm form, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.spaceId = spaceId;
    this.authorUserId = authorUserId;
    this.updatedByUserId = authorUserId;
    this.title = form.title();
    this.content = form.content();
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void edit(UUID editorUserId, WikiPageForm form, OffsetDateTime now) {
    this.title = form.title();
    this.content = form.content();
    this.updatedByUserId = editorUserId;
    this.updatedAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getSpaceId() { return spaceId; }
  public String getTitle() { return title; }
  public String getContent() { return content; }
  public UUID getAuthorUserId() { return authorUserId; }
  public UUID getUpdatedByUserId() { return updatedByUserId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
