package com.moara.moa.wiki;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 위키 첨부 저장/조회/삭제. 파일 본체는 <저장루트>/<tenantId>/<attachmentId> 로 기록하고(파일명 UUID → 경로조작 불가),
 * 메타는 DB에 둔다. 조회/삭제는 항상 tenant_id로 스코프한다(테넌트 격리). 원본 파일명은 표시·다운로드용으로만 쓴다.
 */
@Service
public class WikiAttachmentService {
  private final WikiAttachmentRepository repository;
  private final Path storageRoot;

  public WikiAttachmentService(
      WikiAttachmentRepository repository,
      @Value("${moa.wiki.attachment-dir:data/wiki-attachments}") String attachmentDir) {
    this.repository = repository;
    this.storageRoot = Path.of(attachmentDir).toAbsolutePath().normalize();
  }

  public List<WikiAttachment> list(UUID tenantId, UUID pageId) {
    return repository.findByTenantIdAndPageIdOrderByCreatedAtDesc(tenantId, pageId);
  }

  public WikiAttachment find(UUID tenantId, UUID attachmentId) {
    return repository.findByIdAndTenantId(attachmentId, tenantId)
        .orElseThrow(() -> new WikiAttachmentNotFoundException(attachmentId));
  }

  /** 첨부 저장: 빈 파일 거부, 원본 파일명은 경로 없이 정제해 보관, 본체는 UUID 이름으로 기록. */
  public WikiAttachment store(UUID tenantId, UUID pageId, UUID uploaderId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("빈 파일은 첨부할 수 없습니다.");
    }
    UUID id = UUID.randomUUID();
    String relative = tenantId + "/" + id;
    Path target = resolveWithin(relative);
    try {
      Files.createDirectories(target.getParent());
      file.transferTo(target);
    } catch (IOException exception) {
      throw new UncheckedIOException("첨부 저장에 실패했습니다.", exception);
    }
    WikiAttachment attachment = new WikiAttachment(
        id, tenantId, pageId, safeName(file.getOriginalFilename()), file.getContentType(),
        file.getSize(), relative, uploaderId, OffsetDateTime.now());
    return repository.save(attachment);
  }

  /** 첨부 본체 경로(다운로드/미리보기용). 파일이 없으면 예외. */
  public Path resolve(WikiAttachment attachment) {
    Path path = resolveWithin(attachment.getStoragePath());
    if (!Files.exists(path)) {
      throw new WikiAttachmentNotFoundException(attachment.getId());
    }
    return path;
  }

  public void delete(UUID tenantId, UUID attachmentId) {
    WikiAttachment attachment = find(tenantId, attachmentId);
    deleteFileQuietly(attachment);
    repository.delete(attachment);
  }

  /** 페이지 삭제 시 그 페이지의 첨부 파일 본체와 메타를 함께 정리한다(FK CASCADE는 메타만 지우므로). */
  public void deleteAllForPage(UUID tenantId, UUID pageId) {
    List<WikiAttachment> attachments = list(tenantId, pageId);
    for (WikiAttachment attachment : attachments) {
      deleteFileQuietly(attachment);
    }
    repository.deleteAll(attachments);
  }

  private void deleteFileQuietly(WikiAttachment attachment) {
    try {
      Files.deleteIfExists(resolveWithin(attachment.getStoragePath()));
    } catch (IOException exception) {
      throw new UncheckedIOException("첨부 삭제에 실패했습니다.", exception);
    }
  }

  /** 저장 루트 밖으로 나가지 못하게 정규화 후 경계 검증(경로조작 방어). */
  private Path resolveWithin(String relative) {
    Path path = storageRoot.resolve(relative).normalize();
    if (!path.startsWith(storageRoot)) {
      throw new IllegalArgumentException("허용되지 않은 첨부 경로입니다.");
    }
    return path;
  }

  /** 원본 파일명에서 경로 구분자를 제거하고 255자로 제한(표시·다운로드 용도로만 사용). */
  private String safeName(String original) {
    if (original == null || original.isBlank()) {
      return "file";
    }
    String base = original.replace('\\', '/');
    int slash = base.lastIndexOf('/');
    String name = slash >= 0 ? base.substring(slash + 1) : base;
    name = name.trim();
    if (name.isEmpty()) {
      return "file";
    }
    return name.length() > 255 ? name.substring(name.length() - 255) : name;
  }
}
