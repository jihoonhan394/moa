package com.moara.moa.maintenance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 유지보수 담당자: 특정 자산/솔루션의 책임자(알림 대상). */
@Entity
@Table(name = "maintenance_owners")
public class MaintenanceOwner {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "target_type")
  @Enumerated(EnumType.STRING)
  private MaintenanceTargetType targetType;

  @Column(name = "target_id")
  private UUID targetId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  protected MaintenanceOwner() {}

  public MaintenanceOwner(
      UUID id, UUID tenantId, MaintenanceTargetType targetType, UUID targetId, UUID userId, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.targetType = targetType;
    this.targetId = targetId;
    this.userId = userId;
    this.createdAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public MaintenanceTargetType getTargetType() { return targetType; }
  public UUID getTargetId() { return targetId; }
  public UUID getUserId() { return userId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
