package com.moara.moa.permission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 묶음 권한을 그룹에 부착(주력). 그룹 멤버 전원에게 해당 권한의 엔트리가 적용된다. */
@Entity
@Table(name = "permission_group_assignments")
public class PermissionGroupAssignment {
  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "permission_id", nullable = false)
  private UUID permissionId;

  @Column(name = "group_id", nullable = false)
  private UUID groupId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected PermissionGroupAssignment() {}

  public PermissionGroupAssignment(UUID id, UUID tenantId, UUID permissionId, UUID groupId, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.permissionId = permissionId;
    this.groupId = groupId;
    this.createdAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getPermissionId() { return permissionId; }
  public UUID getGroupId() { return groupId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
