package com.moara.moa.wiki;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 위키 공간(폴더). parentId=null이면 최상위. 서브폴더로 계층을 이룬다. 권한은 wiki_space_permissions. */
@Entity
@Table(name = "wiki_spaces")
public class WikiSpace {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "parent_id")
  private UUID parentId;

  private String name;

  private String description;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  protected WikiSpace() {}

  public WikiSpace(UUID id, UUID tenantId, UUID parentId, WikiSpaceForm form, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.parentId = parentId;
    this.createdAt = now;
    rename(form, now);
  }

  public void rename(WikiSpaceForm form, OffsetDateTime now) {
    this.name = form.name();
    this.description = form.description() == null || form.description().isBlank()
        ? null : form.description().trim();
    this.updatedAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getParentId() { return parentId; }
  public String getName() { return name; }
  public String getDescription() { return description; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
