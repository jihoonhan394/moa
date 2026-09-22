package com.moara.moa.wiki;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 위키 첨부파일 메타데이터. 파일 본체는 서버 파일시스템(기관별 하위 디렉터리)에 UUID 이름으로 저장하고,
 * 여기엔 원본 파일명·타입·크기·상대 저장경로만 둔다. 조회/삭제는 항상 tenant_id로 스코프한다.
 */
@Entity
@Table(name = "wiki_attachments")
public class WikiAttachment {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "page_id", nullable = false)
  private UUID pageId;

  @Column(nullable = false)
  private String filename;

  @Column(name = "content_type")
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "storage_path", nullable = false)
  private String storagePath;

  @Column(name = "uploaded_by_user_id")
  private UUID uploadedByUserId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected WikiAttachment() {}

  public WikiAttachment(
      UUID id, UUID tenantId, UUID pageId, String filename, String contentType,
      long sizeBytes, String storagePath, UUID uploadedByUserId, OffsetDateTime createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.pageId = pageId;
    this.filename = filename;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.storagePath = storagePath;
    this.uploadedByUserId = uploadedByUserId;
    this.createdAt = createdAt;
  }

  /** 이미지 첨부인지(목록에서 인라인 미리보기 여부 판단용). */
  public boolean isImage() {
    return contentType != null && contentType.startsWith("image/");
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getPageId() { return pageId; }
  public String getFilename() { return filename; }
  public String getContentType() { return contentType; }
  public long getSizeBytes() { return sizeBytes; }
  public String getStoragePath() { return storagePath; }
  public UUID getUploadedByUserId() { return uploadedByUserId; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
