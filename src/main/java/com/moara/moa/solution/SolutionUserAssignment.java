package com.moara.moa.solution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 솔루션 사용자 배정: 배정받은 사용자는 그 솔루션을 제어(start/stop/status)할 수 있다. */
@Entity
@Table(name = "solution_user_assignments")
public class SolutionUserAssignment {
  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "solution_id", nullable = false)
  private UUID solutionId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected SolutionUserAssignment() {}

  public SolutionUserAssignment(UUID id, UUID tenantId, UUID solutionId, UUID userId, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.solutionId = solutionId;
    this.userId = userId;
    this.createdAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getSolutionId() { return solutionId; }
  public UUID getUserId() { return userId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
