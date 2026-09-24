package com.moara.moa.wiki;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 위키 첨부 저장/조회/삭제. 파일 본체는 파일시스템에, 메타는 DB에 둔다. 조회/삭제는 항상 tenant_id로
 * 스코프한다(테넌트 격리). 원본 파일명은 표시·다운로드용으로만 쓴다.
 *
 * <h2>저장 레이아웃</h2>
 * <pre>&lt;저장루트&gt;/&lt;tenantId&gt;/&lt;yyyy-MM&gt;/&lt;id 앞 2자&gt;/&lt;attachmentId&gt;</pre>
 * 파일명이 UUID라 경로조작이 불가능한 것은 종전과 같고, 여기에 <b>월 + 16진 2자 샤딩</b>을 더했다.
 * 한 디렉터리에 파일이 무한정 쌓이면 백업·동기화·복구가 전부 파일 수에 비례해 느려지기 때문이다
 * (샤딩으로 디렉터리당 파일 수가 약 1/256로 줄고, 월 단위라 오래된 것만 따로 아카이브할 수 있다).
 *
 * <p>실제 경로는 행마다 {@code storage_path}에 저장되므로 <b>레이아웃을 바꿔도 기존 파일은 그대로
 * 열린다</b> — 이전에 저장된 {@code <tenantId>/<id>} 형식도 계속 동작하며 마이그레이션이 필요 없다.
 *
 * <h2>파일과 DB 중 무엇을 먼저 쓰고 지우나</h2>
 * 둘은 한 트랜잭션으로 묶이지 않으므로 어느 쪽이 먼저 실패해도 한쪽만 남는다. 어느 쪽이 남는 편이
 * 덜 위험한지로 순서를 정했다.
 * <ul>
 *   <li><b>저장</b>: 파일 → DB. DB가 실패하면 방금 쓴 파일을 지워 <i>보상</i>한다. 파일만 남으면
 *       아무도 모르는 쓰레기가 되므로 여기서 정리하는 편이 낫다.</li>
 *   <li><b>삭제</b>: DB → 파일. 파일 삭제가 실패해도 예외를 던지지 않고 로그만 남긴다. 반대로 하면
 *       파일 권한 문제 하나로 첨부·문서 삭제 전체가 막히고, 무엇보다 <i>메타는 있는데 파일이 없는</i>
 *       상태(다운로드가 깨짐)가 만들어진다. 파일만 남는 쪽이 눈에 덜 띄지만 안전하다.</li>
 * </ul>
 */
@Service
public class WikiAttachmentService {
  private static final Logger log = LoggerFactory.getLogger(WikiAttachmentService.class);
  private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

  private final WikiAttachmentRepository repository;
  private final Path storageRoot;
  private final long quotaBytes;

  public WikiAttachmentService(
      WikiAttachmentRepository repository,
      @Value("${moa.wiki.attachment-dir:data/wiki-attachments}") String attachmentDir,
      @Value("${moa.wiki.attachment-quota-mb:2048}") long quotaMegabytes) {
    this.repository = repository;
    this.storageRoot = Path.of(attachmentDir).toAbsolutePath().normalize();
    this.quotaBytes = quotaMegabytes * 1024L * 1024L;
  }

  public List<WikiAttachment> list(UUID tenantId, UUID pageId) {
    return repository.findByTenantIdAndPageIdOrderByCreatedAtDesc(tenantId, pageId);
  }

  public WikiAttachment find(UUID tenantId, UUID attachmentId) {
    return repository.findByIdAndTenantId(attachmentId, tenantId)
        .orElseThrow(() -> new WikiAttachmentNotFoundException(attachmentId));
  }

  /** 기관이 첨부로 쓰고 있는 총 용량(바이트). 설정 화면·용량 안내에 쓴다. */
  public long usedBytes(UUID tenantId) {
    return repository.sumSizeBytesByTenantId(tenantId);
  }

  /** 기관별 첨부 용량 상한(바이트). 0 이하면 상한 없음. */
  public long quotaBytes() {
    return quotaBytes;
  }

  /**
   * 첨부 저장: 빈 파일 거부, 기관 용량 상한 확인, 원본 파일명은 경로 없이 정제해 보관, 본체는
   * 샤딩된 경로에 UUID 이름으로 기록.
   */
  public WikiAttachment store(UUID tenantId, UUID pageId, UUID uploaderId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("빈 파일은 첨부할 수 없습니다.");
    }
    requireQuota(tenantId, file.getSize());
    UUID id = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now();
    String relative = storagePathFor(tenantId, id, now);
    Path target = resolveWithin(relative);
    try {
      Files.createDirectories(target.getParent());
      file.transferTo(target);
    } catch (IOException exception) {
      throw new UncheckedIOException("첨부 저장에 실패했습니다.", exception);
    }
    WikiAttachment attachment = new WikiAttachment(
        id, tenantId, pageId, safeName(file.getOriginalFilename()), file.getContentType(),
        file.getSize(), relative, uploaderId, now);
    try {
      return repository.save(attachment);
    } catch (RuntimeException failure) {
      // 메타 저장이 실패하면 방금 쓴 파일은 아무도 참조하지 않는 쓰레기가 된다. 여기서 되돌린다.
      deleteFileQuietly(relative, id);
      throw failure;
    }
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
    repository.delete(attachment);
    deleteFileQuietly(attachment.getStoragePath(), attachment.getId());
  }

  /** 페이지 삭제 시 그 페이지의 첨부 파일 본체와 메타를 함께 정리한다(FK CASCADE는 메타만 지우므로). */
  public void deleteAllForPage(UUID tenantId, UUID pageId) {
    List<WikiAttachment> attachments = list(tenantId, pageId);
    repository.deleteAll(attachments);
    for (WikiAttachment attachment : attachments) {
      deleteFileQuietly(attachment.getStoragePath(), attachment.getId());
    }
  }

  /**
   * 기관 첨부 총량이 상한을 넘지 않는지 확인한다. 상한이 없으면(0 이하) 건너뛴다.
   * 동시 업로드에서는 근소하게 초과할 수 있으나, 목적이 회계가 아니라 폭주 방지이므로 허용한다.
   */
  private void requireQuota(UUID tenantId, long incomingBytes) {
    if (quotaBytes <= 0) {
      return;
    }
    long used = usedBytes(tenantId);
    if (used + incomingBytes > quotaBytes) {
      long limitMb = quotaBytes / (1024L * 1024L);
      long usedMb = used / (1024L * 1024L);
      throw new IllegalArgumentException(
          "기관 첨부 용량 상한(" + limitMb + "MB)을 초과합니다. 현재 사용 " + usedMb
              + "MB — 오래된 첨부를 정리한 뒤 다시 시도하세요.");
    }
  }

  /**
   * 저장 상대 경로: {@code <tenantId>/<yyyy-MM>/<id 앞 2자>/<id>}.
   * 월로 나눠 오래된 것을 통째로 아카이브할 수 있게 하고, 16진 2자로 한 번 더 나눠 디렉터리당
   * 파일 수를 약 1/256로 낮춘다.
   */
  private String storagePathFor(UUID tenantId, UUID id, OffsetDateTime at) {
    String shard = id.toString().substring(0, 2);
    return tenantId + "/" + MONTH.format(at) + "/" + shard + "/" + id;
  }

  /**
   * 파일 삭제 실패는 로그만 남기고 삼킨다(이름 그대로 quietly). DB에서 이미 지워진 뒤이므로 여기서
   * 예외를 던지면 사용자 작업만 실패하고 정작 파일은 그대로다. 남은 파일은 로그로 추적한다.
   */
  private void deleteFileQuietly(String relativePath, UUID attachmentId) {
    try {
      Files.deleteIfExists(resolveWithin(relativePath));
    } catch (IOException | RuntimeException exception) {
      log.warn("첨부 파일 삭제 실패 — 고아 파일이 남았습니다. attachmentId={} path={}",
          attachmentId, relativePath, exception);
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
