package com.moara.moa.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 관리자 행위 감사 로그. append-only(감사 기준) — 수정/삭제 메서드를 두지 않는다.
 * TENANT 범위는 tenant_id가 필수, GLOBAL 범위는 tenant_id가 null이어야 한다(팩토리에서 강제).
 * message에는 자격증명 등 민감정보를 담지 않는다.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {
  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_scope", nullable = false, length = 20)
  private AuditActionScope actionScope;

  @Column(name = "tenant_id")
  private UUID tenantId;

  @Column(name = "target_tenant_id")
  private UUID targetTenantId;

  @Column(name = "actor_user_id", nullable = false)
  private UUID actorUserId;

  @Column(nullable = false, length = 100)
  private String action;

  @Column(name = "target_type", length = 50)
  private String targetType;

  @Column(name = "target_id")
  private UUID targetId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AuditResult result;

  @Column(length = 1000)
  private String message;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected AuditLog() {}

  private AuditLog(
      UUID id, AuditActionScope actionScope, UUID tenantId, UUID targetTenantId, UUID actorUserId,
      String action, String targetType, UUID targetId, AuditResult result, String message, OffsetDateTime now) {
    this.id = id;
    this.actionScope = actionScope;
    this.tenantId = tenantId;
    this.targetTenantId = targetTenantId;
    this.actorUserId = actorUserId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.result = result;
    this.message = message;
    this.createdAt = now;
  }

  /** 테넌트 내 행위(tenant_id 필수). */
  public static AuditLog tenantAction(
      UUID id, UUID tenantId, UUID actorUserId, String action, String targetType, UUID targetId,
      AuditResult result, String message, OffsetDateTime now) {
    if (tenantId == null) {
      throw new IllegalArgumentException("TENANT 범위 감사 로그에는 tenantId가 필요합니다.");
    }
    return new AuditLog(id, AuditActionScope.TENANT, tenantId, null, actorUserId,
        action, targetType, targetId, result, message, now);
  }

  /** 전역 행위(tenant_id 없음). 특정 테넌트 대상 행위면 targetTenantId로 남긴다. */
  public static AuditLog globalAction(
      UUID id, UUID actorUserId, UUID targetTenantId, String action, String targetType, UUID targetId,
      AuditResult result, String message, OffsetDateTime now) {
    return new AuditLog(id, AuditActionScope.GLOBAL, null, targetTenantId, actorUserId,
        action, targetType, targetId, result, message, now);
  }

  public UUID getId() { return id; }
  public AuditActionScope getActionScope() { return actionScope; }
  public UUID getTenantId() { return tenantId; }
  public UUID getTargetTenantId() { return targetTenantId; }
  public UUID getActorUserId() { return actorUserId; }
  public String getAction() { return action; }
  public String getTargetType() { return targetType; }
  public UUID getTargetId() { return targetId; }
  public AuditResult getResult() { return result; }
  public String getMessage() { return message; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
