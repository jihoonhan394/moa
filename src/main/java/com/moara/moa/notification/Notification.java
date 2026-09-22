package com.moara.moa.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 사용자별 인앱 알림. link는 클릭 시 이동할 경로(선택). */
@Entity
@Table(name = "notifications")
public class Notification {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  private String title;

  private String body;

  private String link;

  @Column(name = "is_read")
  private boolean read;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  protected Notification() {}

  public Notification(UUID id, UUID tenantId, UUID userId, String title, String body, String link, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.userId = userId;
    this.title = title;
    this.body = body;
    this.link = link;
    this.read = false;
    this.createdAt = now;
  }

  public void markRead() {
    this.read = true;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getUserId() { return userId; }
  public String getTitle() { return title; }
  public String getBody() { return body; }
  public String getLink() { return link; }
  public boolean isRead() { return read; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
