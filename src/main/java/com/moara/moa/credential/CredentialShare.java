package com.moara.moa.credential;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 크리덴셜 열람 공유: 특정 자격증명을 특정 사용자가 볼 수 있게(비IT 사용자용). */
@Entity
@Table(name = "credential_shares")
public class CredentialShare {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "credential_id", nullable = false)
  private UUID credentialId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected CredentialShare() {}

  public CredentialShare(UUID id, UUID tenantId, UUID credentialId, UUID userId, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.credentialId = credentialId;
    this.userId = userId;
    this.createdAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getCredentialId() { return credentialId; }
  public UUID getUserId() { return userId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
