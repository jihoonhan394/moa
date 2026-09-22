package com.moara.moa.access;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 접근요청(JIT 티켓). 요청자가 특정 자산에 대해 사유·기간을 적어 신청하고, 승인되면 [시작~종료] 창 동안만
 * 임시 접속이 허용된다(만료=자동 반납). 승인 시 볼트 자격증명을 함께 내줄 수 있다(credentialId).
 * 모든 상태 전이는 서비스에서 감사·알림과 함께 수행한다.
 */
@Entity
@Table(name = "access_requests")
public class AccessRequest {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "requester_user_id", nullable = false)
  private UUID requesterUserId;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  private String reason;

  @Column(name = "requested_start_at", nullable = false)
  private OffsetDateTime requestedStartAt;

  @Column(name = "requested_end_at", nullable = false)
  private OffsetDateTime requestedEndAt;

  @Enumerated(EnumType.STRING)
  private AccessRequestStatus status;

  @Column(name = "approver_user_id")
  private UUID approverUserId;

  @Column(name = "credential_id")
  private UUID credentialId;

  @Column(name = "review_comment")
  private String reviewComment;

  @Column(name = "decided_at")
  private OffsetDateTime decidedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected AccessRequest() {}

  public static AccessRequest create(
      UUID tenantId, UUID requesterUserId, UUID assetId, String reason,
      OffsetDateTime start, OffsetDateTime end, OffsetDateTime now) {
    AccessRequest request = new AccessRequest();
    request.id = UUID.randomUUID();
    request.tenantId = tenantId;
    request.requesterUserId = requesterUserId;
    request.assetId = assetId;
    request.reason = reason == null ? null : reason.trim();
    request.requestedStartAt = start;
    request.requestedEndAt = end;
    request.status = AccessRequestStatus.PENDING;
    request.createdAt = now;
    return request;
  }

  /** 승인: 상태 APPROVED + 승인자·(선택)자격증명·코멘트 기록. */
  public void approve(UUID approverUserId, UUID credentialId, String comment, OffsetDateTime now) {
    this.status = AccessRequestStatus.APPROVED;
    this.approverUserId = approverUserId;
    this.credentialId = credentialId;
    this.reviewComment = trimToNull(comment);
    this.decidedAt = now;
  }

  public void reject(UUID approverUserId, String comment, OffsetDateTime now) {
    this.status = AccessRequestStatus.REJECTED;
    this.approverUserId = approverUserId;
    this.reviewComment = trimToNull(comment);
    this.decidedAt = now;
  }

  public void cancel(OffsetDateTime now) {
    this.status = AccessRequestStatus.CANCELED;
    this.decidedAt = now;
  }

  public void revoke(UUID approverUserId, String comment, OffsetDateTime now) {
    this.status = AccessRequestStatus.REVOKED;
    this.approverUserId = approverUserId;
    this.reviewComment = trimToNull(comment);
    this.decidedAt = now;
  }

  public void expire() {
    this.status = AccessRequestStatus.EXPIRED;
  }

  /** 지금 접속 가능한 상태인가(승인됨 + 현재가 [시작,종료) 창 안). */
  public boolean isActiveAt(OffsetDateTime now) {
    return status == AccessRequestStatus.APPROVED
        && !now.isBefore(requestedStartAt)
        && now.isBefore(requestedEndAt);
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getRequesterUserId() { return requesterUserId; }
  public UUID getAssetId() { return assetId; }
  public String getReason() { return reason; }
  public OffsetDateTime getRequestedStartAt() { return requestedStartAt; }
  public OffsetDateTime getRequestedEndAt() { return requestedEndAt; }
  public AccessRequestStatus getStatus() { return status; }
  public UUID getApproverUserId() { return approverUserId; }
  public UUID getCredentialId() { return credentialId; }
  public String getReviewComment() { return reviewComment; }
  public OffsetDateTime getDecidedAt() { return decidedAt; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
