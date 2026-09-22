package com.moara.moa.onboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 입사 온보딩 템플릿(부서/직무 단위). 예: "영업팀" → 영업 위키·솔루션·필독문서 묶음. */
@Entity
@Table(name = "onboarding_templates")
public class OnboardingTemplate {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  private String name;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  protected OnboardingTemplate() {}

  public OnboardingTemplate(UUID id, UUID tenantId, String name, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.name = name == null ? null : name.trim();
    this.createdAt = now;
  }

  public void rename(String name) {
    this.name = name == null ? null : name.trim();
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public String getName() { return name; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
