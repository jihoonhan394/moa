package com.moara.moa.solution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 기동 순서의 한 단계. 특정 솔루션을 가리키며 position으로 순서를, wait_seconds로 시작 후 대기를 정한다. */
@Entity
@Table(name = "solution_sequence_steps")
public class SolutionSequenceStep {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "sequence_id", nullable = false)
  private UUID sequenceId;

  @Column(name = "solution_id", nullable = false)
  private UUID solutionId;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  private ControlAction action;

  private int position;

  @Column(name = "wait_seconds")
  private int waitSeconds;

  @Column(name = "verify_after_start")
  private boolean verifyAfterStart;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  protected SolutionSequenceStep() {}

  public SolutionSequenceStep(
      UUID id, UUID tenantId, UUID sequenceId, UUID solutionId, ControlAction action, int position,
      int waitSeconds, boolean verifyAfterStart, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.sequenceId = sequenceId;
    this.solutionId = solutionId;
    this.action = action;
    this.position = position;
    this.waitSeconds = waitSeconds;
    this.verifyAfterStart = verifyAfterStart;
    this.createdAt = now;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getSequenceId() { return sequenceId; }
  public UUID getSolutionId() { return solutionId; }
  public ControlAction getAction() { return action; }
  public int getPosition() { return position; }
  public int getWaitSeconds() { return waitSeconds; }
  public boolean isVerifyAfterStart() { return verifyAfterStart; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
