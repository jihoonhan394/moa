package com.moara.moa.deputy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 팀 내 대직(임시 부재 권한). 부재 팀원(absent)의 운영 접근을 대직자(deputy)가 기간 안에서만 상속한다.
 * 활성 = starts_on ≤ 오늘 ≤ ends_on. 역할을 복사하지 않으므로 종료일이 지나면 자동 원복(계산형).
 */
@Entity
@Table(name = "deputy_delegations")
public class DeputyDelegation {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "absent_user_id", nullable = false)
  private UUID absentUserId;

  @Column(name = "deputy_user_id", nullable = false)
  private UUID deputyUserId;

  @Column(name = "starts_on", nullable = false)
  private LocalDate startsOn;

  @Column(name = "ends_on", nullable = false)
  private LocalDate endsOn;

  @Column(name = "created_by_user_id")
  private UUID createdByUserId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected DeputyDelegation() {}

  public DeputyDelegation(
      UUID id, UUID tenantId, UUID absentUserId, UUID deputyUserId,
      LocalDate startsOn, LocalDate endsOn, UUID createdByUserId, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.absentUserId = absentUserId;
    this.deputyUserId = deputyUserId;
    this.startsOn = startsOn;
    this.endsOn = endsOn;
    this.createdByUserId = createdByUserId;
    this.createdAt = now;
  }

  public boolean isActiveOn(LocalDate day) {
    return !day.isBefore(startsOn) && !day.isAfter(endsOn);
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getAbsentUserId() { return absentUserId; }
  public UUID getDeputyUserId() { return deputyUserId; }
  public LocalDate getStartsOn() { return startsOn; }
  public LocalDate getEndsOn() { return endsOn; }
  public UUID getCreatedByUserId() { return createdByUserId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
