package com.moara.moa.notice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 기관 공지사항. 본문은 순수 텍스트로 보관하고 화면에서 th:text로만 출력한다(HTML 미허용). */
@Entity
@Table(name = "notices")
public class Notice {
  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, length = 4000)
  private String body;

  @Column(name = "author_id")
  private UUID authorId;

  @Column(name = "author_name", length = 100)
  private String authorName;

  @Column(nullable = false)
  private boolean pinned;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Notice() {}

  public static Notice create(
      UUID tenantId, String title, String body, boolean pinned,
      UUID authorId, String authorName, OffsetDateTime now) {
    Notice notice = new Notice();
    notice.id = UUID.randomUUID();
    notice.tenantId = tenantId;
    notice.authorId = authorId;
    notice.authorName = authorName;
    notice.apply(title, body, pinned, now);
    notice.createdAt = now;
    return notice;
  }

  public void apply(String title, String body, boolean pinned, OffsetDateTime now) {
    this.title = title == null ? null : title.trim();
    this.body = body == null ? null : body.trim();
    this.pinned = pinned;
    this.updatedAt = now;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public String getTitle() { return title; }
  public String getBody() { return body; }
  public UUID getAuthorId() { return authorId; }
  public String getAuthorName() { return authorName; }
  public boolean isPinned() { return pinned; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
