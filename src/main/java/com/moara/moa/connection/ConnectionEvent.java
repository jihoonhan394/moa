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
 * 접속 이벤트 append-only 이력(감사 기준). 생성 후 변경 불가 — 수정/삭제 메서드를 두지 않는다.
 * message에는 민감정보(비밀번호/키)를 담지 않는다.
 */
@Entity
@Table(name = "connection_events")
public class ConnectionEvent {
  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "connection_session_id", nullable = false)
  private UUID connectionSessionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 20)
  private ConnectionEventType eventType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ConnectionEventResult result;

  @Column(length = 1000)
  private String message;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ConnectionEvent() {}

  public ConnectionEvent(
      UUID id, UUID tenantId, UUID connectionSessionId, ConnectionEventType eventType,
      ConnectionEventResult result, String message, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.connectionSessionId = connectionSessionId;
    this.eventType = eventType;
    this.result = result;
    this.message = message;
    this.createdAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getConnectionSessionId() { return connectionSessionId; }
  public ConnectionEventType getEventType() { return eventType; }
  public ConnectionEventResult getResult() { return result; }
  public String getMessage() { return message; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
