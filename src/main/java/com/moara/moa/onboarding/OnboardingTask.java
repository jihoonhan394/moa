package com.moara.moa.onboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 신입 개인 온보딩 체크리스트 항목(템플릿 적용 시 TASK/ACK_DOC 항목이 복사된다). */
@Entity
@Table(name = "onboarding_tasks")
public class OnboardingTask {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "kind")
  @Enumerated(EnumType.STRING)
  private OnboardingItemType kind;

  private String label;

  @Column(name = "ref_id")
  private UUID refId;

  private boolean done;

  @Column(name = "done_at")
  private OffsetDateTime doneAt;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  protected OnboardingTask() {}

  public OnboardingTask(
      UUID id, UUID tenantId, UUID userId, OnboardingItemType kind, String label, UUID refId, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.userId = userId;
    this.kind = kind;
    this.label = label;
    this.refId = refId;
    this.done = false;
    this.createdAt = now;
  }

  /** 완료 처리(멱등). ACK_DOC이면 "읽고 동의"의 의미. */
  public void complete(OffsetDateTime now) {
    if (!this.done) {
      this.done = true;
      this.doneAt = now;
    }
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getUserId() { return userId; }
  public OnboardingItemType getKind() { return kind; }
  public String getLabel() { return label; }
  public UUID getRefId() { return refId; }
  public boolean isDone() { return done; }
  public OffsetDateTime getDoneAt() { return doneAt; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
