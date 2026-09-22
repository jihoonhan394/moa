package com.moara.moa.wiki;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 위키 공간 권한: (전체/그룹/개인) → 접근 수준(열람/편집/관리). MANAGE=부서장(공간 오너). */
@Entity
@Table(name = "wiki_space_permissions")
public class WikiSpacePermission {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "space_id", nullable = false)
  private UUID spaceId;

  @Column(name = "subject_type")
  @Enumerated(EnumType.STRING)
  private WikiSubjectType subjectType;

  @Column(name = "subject_id")
  private UUID subjectId;

  @Column(name = "access_level")
  @Enumerated(EnumType.STRING)
  private WikiAccessLevel accessLevel;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  protected WikiSpacePermission() {}

  public WikiSpacePermission(
      UUID id, UUID tenantId, UUID spaceId, WikiSubjectType subjectType, UUID subjectId,
      WikiAccessLevel accessLevel, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.spaceId = spaceId;
    this.subjectType = subjectType;
    this.subjectId = subjectId;
    this.accessLevel = accessLevel;
    this.createdAt = now;
  }

  public void changeLevel(WikiAccessLevel level) {
    this.accessLevel = level;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getSpaceId() { return spaceId; }
  public WikiSubjectType getSubjectType() { return subjectType; }
  public UUID getSubjectId() { return subjectId; }
  public WikiAccessLevel getAccessLevel() { return accessLevel; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
