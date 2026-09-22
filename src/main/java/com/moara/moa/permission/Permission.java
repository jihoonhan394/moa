package com.moara.moa.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 명명된 묶음 권한(Permission Set). 여러 {@link PermissionEntry}(자산×액션)를 담고,
 * 그룹/사용자에 부착되어 재사용된다. 이름은 테넌트 내 유일하다.
 */
@Entity
@Table(name = "permissions")
public class Permission {
  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 255)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PermissionStatus status;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Permission() {}

  public Permission(UUID id, UUID tenantId, PermissionForm form, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.createdAt = now;
    apply(form, now);
  }

  public void apply(PermissionForm form, OffsetDateTime now) {
    this.name = form.name().trim();
    this.description = form.description() == null || form.description().isBlank() ? null : form.description().trim();
    this.status = form.status() == null ? PermissionStatus.ACTIVE : form.status();
    this.updatedAt = now;
  }

  public void disable(OffsetDateTime now) {
    this.status = PermissionStatus.DISABLED;
    this.updatedAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public String getName() { return name; }
  public String getDescription() { return description; }
  public PermissionStatus getStatus() { return status; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
