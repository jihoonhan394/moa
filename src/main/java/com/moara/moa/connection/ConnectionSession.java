package com.moara.moa.connection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 접속 세션 요약(감사축). 한 접속 시도/세션 = 한 행. 상태는 ATTEMPTING에서 시작해
 * CONNECTED/FAILED를 거쳐 CLOSED로 전이한다. failure_code에는 분류값만 담고 자격증명은 담지 않는다.
 */
@Entity
@Table(name = "connection_sessions")
public class ConnectionSession {
  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "session_id", nullable = false, length = 100)
  private String sessionId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SessionProtocol protocol;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ConnectionStatus status;

  @Column(name = "client_ip", length = 45)
  private String clientIp;

  @Column(name = "failure_code", length = 50)
  private String failureCode;

  @Column(name = "started_at")
  private OffsetDateTime startedAt;

  @Column(name = "ended_at")
  private OffsetDateTime endedAt;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected ConnectionSession() {}

  public ConnectionSession(
      UUID id, UUID tenantId, String sessionId, UUID userId, UUID assetId,
      SessionProtocol protocol, String clientIp, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.sessionId = sessionId;
    this.userId = userId;
    this.assetId = assetId;
    this.protocol = protocol;
    this.clientIp = clientIp;
    this.status = ConnectionStatus.ATTEMPTING;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void markConnected(OffsetDateTime now) {
    this.status = ConnectionStatus.CONNECTED;
    this.startedAt = now;
    this.updatedAt = now;
  }

  public void markFailed(String failureCode, OffsetDateTime now) {
    this.status = ConnectionStatus.FAILED;
    this.failureCode = failureCode;
    this.endedAt = now;
    this.updatedAt = now;
  }

  public void markClosed(OffsetDateTime now) {
    this.status = ConnectionStatus.CLOSED;
    this.endedAt = now;
    this.updatedAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public String getSessionId() { return sessionId; }
  public UUID getUserId() { return userId; }
  public UUID getAssetId() { return assetId; }
  public SessionProtocol getProtocol() { return protocol; }
  public ConnectionStatus getStatus() { return status; }
  public String getClientIp() { return clientIp; }
  public String getFailureCode() { return failureCode; }
  public OffsetDateTime getStartedAt() { return startedAt; }
  public OffsetDateTime getEndedAt() { return endedAt; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
