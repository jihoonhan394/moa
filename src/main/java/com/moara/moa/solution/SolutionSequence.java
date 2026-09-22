package com.moara.moa.solution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 솔루션 기동 순서(오케스트레이션 체인). 단계는 solution_sequence_steps에 순서대로 담긴다. */
@Entity
@Table(name = "solution_sequences")
public class SolutionSequence {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  private String name;

  private String description;

  @Column(name = "forward_cron")
  private String forwardCron;

  @Column(name = "reverse_cron")
  private String reverseCron;

  @Column(name = "last_forward_fired")
  private String lastForwardFired;

  @Column(name = "last_reverse_fired")
  private String lastReverseFired;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  protected SolutionSequence() {}

  public SolutionSequence(UUID id, UUID tenantId, SolutionSequenceForm form, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.createdAt = now;
    apply(form, now);
  }

  public void apply(SolutionSequenceForm form, OffsetDateTime now) {
    this.name = form.name();
    this.description = form.description() == null || form.description().isBlank() ? null : form.description().trim();
    this.updatedAt = now;
  }

  /** cron 스케줄 설정(빈 값=해제). forward=정방향 자동 실행, reverse=역방향. */
  public void setSchedule(String forwardCron, String reverseCron, OffsetDateTime now) {
    this.forwardCron = blankToNull(forwardCron);
    this.reverseCron = blankToNull(reverseCron);
    this.updatedAt = now;
  }

  public void markForwardFired(String minuteKey) { this.lastForwardFired = minuteKey; }

  public void markReverseFired(String minuteKey) { this.lastReverseFired = minuteKey; }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public String getName() { return name; }
  public String getDescription() { return description; }
  public String getForwardCron() { return forwardCron; }
  public String getReverseCron() { return reverseCron; }
  public String getLastForwardFired() { return lastForwardFired; }
  public String getLastReverseFired() { return lastReverseFired; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
