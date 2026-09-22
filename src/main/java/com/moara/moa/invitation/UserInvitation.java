package com.moara.moa.invitation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 사용자 초대. 관리자/부서장이 이메일로 초대하며 부서(group)·역할·온보딩 템플릿을 미리 지정한다.
 * 수락 링크의 토큰은 <b>해시로만</b> 저장(원문 미보관). 수락 시 계정이 생성되고 지정 부서·역할·온보딩이 적용된다.
 */
@Entity
@Table(name = "user_invitations")
public class UserInvitation {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  private String email;

  private String name;

  @Column(name = "group_id")
  private UUID groupId;

  private String roles;

  @Column(name = "onboarding_template_id")
  private UUID onboardingTemplateId;

  @Column(name = "token_hash", nullable = false)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  private InvitationStatus status;

  @Column(name = "invited_by_user_id")
  private UUID invitedByUserId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "accepted_at")
  private OffsetDateTime acceptedAt;

  protected UserInvitation() {}

  public UserInvitation(
      UUID id, UUID tenantId, String email, String name, UUID groupId, String roles,
      UUID onboardingTemplateId, String tokenHash, UUID invitedByUserId,
      OffsetDateTime now, OffsetDateTime expiresAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.email = email == null ? null : email.trim().toLowerCase();
    this.name = name == null || name.isBlank() ? null : name.trim();
    this.groupId = groupId;
    this.roles = roles == null || roles.isBlank() ? "USER" : roles;
    this.onboardingTemplateId = onboardingTemplateId;
    this.tokenHash = tokenHash;
    this.invitedByUserId = invitedByUserId;
    this.status = InvitationStatus.PENDING;
    this.createdAt = now;
    this.expiresAt = expiresAt;
  }

  /** 유효(수락 가능) 여부: PENDING이며 만료 전. */
  public boolean isAcceptable(OffsetDateTime now) {
    return status == InvitationStatus.PENDING && expiresAt.isAfter(now);
  }

  public void accept(OffsetDateTime now) {
    this.status = InvitationStatus.ACCEPTED;
    this.acceptedAt = now;
  }

  /** 재발송: 새 토큰으로 교체하고 만료를 갱신한다(다시 PENDING). */
  public void reissue(String tokenHash, OffsetDateTime expiresAt) {
    this.tokenHash = tokenHash;
    this.status = InvitationStatus.PENDING;
    this.expiresAt = expiresAt;
  }

  public void revoke() {
    this.status = InvitationStatus.REVOKED;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public String getEmail() { return email; }
  public String getName() { return name; }
  public UUID getGroupId() { return groupId; }
  public String getRoles() { return roles; }
  public UUID getOnboardingTemplateId() { return onboardingTemplateId; }
  public InvitationStatus getStatus() { return status; }
  public UUID getInvitedByUserId() { return invitedByUserId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getExpiresAt() { return expiresAt; }
  public OffsetDateTime getAcceptedAt() { return acceptedAt; }
}
