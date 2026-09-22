package com.moara.moa.maintenance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 점검/작업 일정(대상 자산·솔루션의 예정된 유지보수 창). */
@Entity
@Table(name = "maintenance_windows")
public class MaintenanceWindow {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "target_type")
  @Enumerated(EnumType.STRING)
  private MaintenanceTargetType targetType;

  @Column(name = "target_id")
  private UUID targetId;

  private String title;

  private String reason;

  @Column(name = "starts_at")
  private LocalDateTime startsAt;

  @Column(name = "ends_at")
  private LocalDateTime endsAt;

  @Column(name = "created_by_user_id")
  private UUID createdByUserId;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  protected MaintenanceWindow() {}

  public MaintenanceWindow(
      UUID id, UUID tenantId, MaintenanceTargetType targetType, UUID targetId, UUID createdByUserId,
      MaintenanceWindowForm form, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.targetType = targetType;
    this.targetId = targetId;
    this.createdByUserId = createdByUserId;
    this.title = form.title();
    this.reason = form.reason() == null || form.reason().isBlank() ? null : form.reason().trim();
    this.startsAt = form.startsAt();
    this.endsAt = form.endsAt();
    this.createdAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public MaintenanceTargetType getTargetType() { return targetType; }
  public UUID getTargetId() { return targetId; }
  public String getTitle() { return title; }
  public String getReason() { return reason; }
  public LocalDateTime getStartsAt() { return startsAt; }
  public LocalDateTime getEndsAt() { return endsAt; }
  public UUID getCreatedByUserId() { return createdByUserId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
