package com.moara.moa.wiki;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 위키 서식(템플릿). 공간에 속하며 새 문서 작성 시 본문으로 불러온다. */
@Entity
@Table(name = "wiki_templates")
public class WikiTemplate {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "space_id", nullable = false)
  private UUID spaceId;

  private String name;

  @Column(columnDefinition = "text")
  private String content;

  @Column(name = "created_by_user_id")
  private UUID createdByUserId;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  protected WikiTemplate() {}

  public WikiTemplate(UUID id, UUID tenantId, UUID spaceId, UUID createdByUserId, WikiTemplateForm form, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.spaceId = spaceId;
    this.createdByUserId = createdByUserId;
    this.createdAt = now;
    apply(form, now);
  }

  public void apply(WikiTemplateForm form, OffsetDateTime now) {
    this.name = form.name();
    this.content = form.content();
    this.updatedAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getSpaceId() { return spaceId; }
  public String getName() { return name; }
  public String getContent() { return content; }
  public UUID getCreatedByUserId() { return createdByUserId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
