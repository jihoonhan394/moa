package com.moara.moa.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "access_groups")
public class AccessGroup {
  @Id private UUID id;

  // 생성 시 확정되며 이후 변경하지 않는다.
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 1000)
  private String description;

  /** 상위 그룹(조직 트리). NULL=최상위. 순환은 서비스에서 차단한다. */
  @Column(name = "parent_id")
  private UUID parentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AccessGroupStatus status;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected AccessGroup() {}

  public AccessGroup(UUID id, UUID tenantId, AccessGroupForm form, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    update(form, now);
    this.parentId = form.parentId();
    this.createdAt = now;
  }

  /** 이름·설명·상태만 수정한다(상위 그룹은 changeParent로 별도 이동 — 순환 검증 필요). */
  public void update(AccessGroupForm form, OffsetDateTime now) {
    this.name = form.name().trim();
    this.description = trimToNull(form.description());
    this.status = form.status() == null ? AccessGroupStatus.ACTIVE : form.status();
    this.updatedAt = now;
  }

  /** 상위 그룹 이동(순환 검증은 서비스에서 끝낸 뒤 호출). */
  public void changeParent(UUID parentId, OffsetDateTime now) {
    this.parentId = parentId;
    this.updatedAt = now;
  }

  /** 물리 삭제 대신 상태 비활성화(감사 추적성 유지). */
  public void disable(OffsetDateTime now) {
    this.status = AccessGroupStatus.DISABLED;
    this.updatedAt = now;
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getParentId() { return parentId; }
  public String getName() { return name; }
  public String getDescription() { return description; }
  public AccessGroupStatus getStatus() { return status; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
