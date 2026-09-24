package com.moara.moa.consumable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * "이거 떨어졌어요" 한 건. 자산 담당자가 볼 수 없는 선반을 <b>쓰는 사람이 대신 봐 주는</b>
 * 장치다 — 지금은 그 정보가 메신저에서 증발한다.
 */
@Entity
@Table(name = "consumable_requests")
public class ConsumableRequest {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "item_id", nullable = false)
  private UUID itemId;

  @Column(name = "requested_by", nullable = false)
  private UUID requestedBy;

  @Column(length = 500)
  private String note;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ConsumableRequestStatus status = ConsumableRequestStatus.REQUESTED;

  @Column(name = "decided_by")
  private UUID decidedBy;

  @Column(name = "decided_at")
  private OffsetDateTime decidedAt;

  @Column(name = "decision_reason", length = 500)
  private String decisionReason;

  @Column(name = "review_on")
  private LocalDate reviewOn;

  @Column(name = "order_id")
  private UUID orderId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ConsumableRequest() {
  }

  public ConsumableRequest(
      UUID id, UUID tenantId, UUID itemId, UUID requestedBy, String note, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.itemId = itemId;
    this.requestedBy = requestedBy;
    this.note = blankToNull(note);
    this.status = ConsumableRequestStatus.REQUESTED;
    this.createdAt = now;
  }

  /** 접수 — "주문할게요". 아직 주문하지 않았다. */
  public void acknowledge(UUID actorId, OffsetDateTime now) {
    decide(ConsumableRequestStatus.ACKNOWLEDGED, actorId, null, null, now);
  }

  /** 거절·보류는 사유가 필수다. 왜 안 됐는지 모르면 요청자는 같은 요청을 반복한다. */
  public void reject(UUID actorId, String reason, OffsetDateTime now) {
    decide(ConsumableRequestStatus.REJECTED, actorId, reason, null, now);
  }

  public void hold(UUID actorId, String reason, LocalDate reviewOn, OffsetDateTime now) {
    decide(ConsumableRequestStatus.HELD, actorId, reason, reviewOn, now);
  }

  /**
   * 주문을 등록하면 완료된다. 주문과 묶어 두면 <b>요청 처리가 곧 주문 이력</b>이 되어,
   * 주기 예측이 먹고 살 데이터가 저절로 쌓인다.
   */
  public void fulfill(UUID orderId, UUID actorId, OffsetDateTime now) {
    this.orderId = orderId;
    decide(ConsumableRequestStatus.FULFILLED, actorId, null, null, now);
  }

  /** 요청자가 직접 거둬들인다. 아직 아무도 손대지 않았을 때만. */
  public boolean cancelableBy(UUID userId) {
    return status == ConsumableRequestStatus.REQUESTED && requestedBy.equals(userId);
  }

  private void decide(
      ConsumableRequestStatus next, UUID actorId, String reason, LocalDate reviewOn,
      OffsetDateTime now) {
    this.status = next;
    this.decidedBy = actorId;
    this.decidedAt = now;
    this.decisionReason = blankToNull(reason);
    this.reviewOn = reviewOn;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public UUID getId() { return id; }

  public UUID getTenantId() { return tenantId; }

  public UUID getItemId() { return itemId; }

  public UUID getRequestedBy() { return requestedBy; }

  public String getNote() { return note; }

  public ConsumableRequestStatus getStatus() { return status; }

  public UUID getDecidedBy() { return decidedBy; }

  public OffsetDateTime getDecidedAt() { return decidedAt; }

  public String getDecisionReason() { return decisionReason; }

  public LocalDate getReviewOn() { return reviewOn; }

  public UUID getOrderId() { return orderId; }

  public OffsetDateTime getCreatedAt() { return createdAt; }
}
