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
 * 묶음 권한의 한 항목: (자산, 액션). 부여 후 불변이며 제거는 행 삭제로 처리한다.
 * action은 {@link PermissionProtocol}과 동일 도메인(자산 등록 프로토콜에서 파생).
 */
@Entity
@Table(name = "permission_entries")
public class PermissionEntry {
  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "permission_id", nullable = false)
  private UUID permissionId;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PermissionProtocol action;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected PermissionEntry() {}

  public PermissionEntry(
      UUID id, UUID tenantId, UUID permissionId, UUID assetId, PermissionProtocol action, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.permissionId = permissionId;
    this.assetId = assetId;
    this.action = action;
    this.createdAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getPermissionId() { return permissionId; }
  public UUID getAssetId() { return assetId; }
  public PermissionProtocol getAction() { return action; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
