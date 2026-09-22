package com.moara.moa.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 묶음 권한을 사용자에 직접 부착(일시 예외). {@code expiresAt}가 지나면 무효다.
 * expiresAt이 null이면 무기한.
 */
@Entity
@Table(name = "permission_user_assignments")
public class PermissionUserAssignment {
  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "permission_id", nullable = false)
  private UUID permissionId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected PermissionUserAssignment() {}

  public PermissionUserAssignment(
      UUID id, UUID tenantId, UUID permissionId, UUID userId, OffsetDateTime expiresAt, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.permissionId = permissionId;
    this.userId = userId;
    this.expiresAt = expiresAt;
    this.createdAt = now;
  }

  /** 만료 시각/무기한 갱신. */
  public void updateExpiry(OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  /** 주어진 시각 기준으로 유효한가(만료 전 또는 무기한). */
  public boolean isActiveAt(OffsetDateTime now) {
    return expiresAt == null || expiresAt.isAfter(now);
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getPermissionId() { return permissionId; }
  public UUID getUserId() { return userId; }
  public OffsetDateTime getExpiresAt() { return expiresAt; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
