package com.moara.moa.wiki;

import com.moara.moa.audit.TenantAuditRecorder;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 위키 문서 첨부(서버 파일시스템 저장). 권한은 첨부가 달린 문서의 공간을 따른다 — 열람 권한이면
 * 내려받기, 편집 권한이면 올리기·삭제.
 *
 * <p>내려받기 응답의 Content-Type·Content-Disposition 결정이 이 컨트롤러의 핵심이다. 업로더가
 * 신고한 타입을 그대로 믿고 inline으로 내보내면 저장형 XSS가 되므로, 안전한 이미지 타입만
 * 화이트리스트로 inline 허용한다.
 */
@Controller
public class WikiAttachmentController {
  /** inline 렌더를 허용하는 이미지 타입(화이트리스트). SVG는 스크립트 실행이 가능해 제외한다. */
  private static final Set<String> INLINE_SAFE_IMAGE_TYPES = Set.of(
      "image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp");

  private final WikiPageService pageService;
  private final WikiAttachmentService attachmentService;
  private final TenantAuditRecorder auditRecorder;
  private final WikiAccessGuard guard;

  public WikiAttachmentController(
      WikiPageService pageService, WikiAttachmentService attachmentService,
      TenantAuditRecorder auditRecorder, WikiAccessGuard guard) {
    this.pageService = pageService;
    this.attachmentService = attachmentService;
    this.auditRecorder = auditRecorder;
    this.guard = guard;
  }

  @PostMapping("/wiki/{id}/attachments")
  public String uploadAttachment(
      @PathVariable UUID id, @RequestParam("file") MultipartFile file,
      RedirectAttributes redirect) {
    UUID tenantId = guard.tenantId();
    WikiPage wikiPage = pageService.findById(tenantId, id);
    guard.requireEdit(wikiPage.getSpaceId());
    try {
      WikiAttachment saved = attachmentService.store(tenantId, id, guard.userId(), file);
      audit("WIKI_ATTACH", id, saved.getFilename());
    } catch (IllegalArgumentException | UncheckedIOException exception) {
      redirect.addFlashAttribute("wikiError", exception.getMessage());
    }
    return "redirect:/wiki/" + id;
  }

  @GetMapping("/wiki/attachments/{attachmentId}")
  public ResponseEntity<Resource> downloadAttachment(@PathVariable UUID attachmentId) {
    UUID tenantId = guard.tenantId();
    WikiAttachment attachment = attachmentService.find(tenantId, attachmentId);
    WikiPage wikiPage = pageService.findById(tenantId, attachment.getPageId());
    guard.requireView(wikiPage.getSpaceId());
    Resource resource = new FileSystemResource(attachmentService.resolve(attachment));
    // 업로더가 신고한 Content-Type은 신뢰할 수 없다. 안전한 이미지 타입만 inline으로 렌더하고
    // 나머지는 전부 첨부(다운로드)로 내린다 — 특히 image/svg+xml은 스크립트를 품을 수 있어
    // inline으로 주면 앱 오리진에서 실행된다(저장형 XSS).
    boolean inlineSafe = isInlineSafeImage(attachment.getContentType());
    ContentDisposition disposition = ContentDisposition
        .builder(inlineSafe ? "inline" : "attachment")
        .filename(attachment.getFilename(), StandardCharsets.UTF_8)
        .build();
    MediaType mediaType = inlineSafe
        ? mediaType(attachment.getContentType())
        : MediaType.APPLICATION_OCTET_STREAM;
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        // 브라우저가 내용을 보고 타입을 추측(sniffing)해 실행하지 못하게 막는다.
        .header("X-Content-Type-Options", "nosniff")
        .contentType(mediaType)
        .body(resource);
  }

  @PostMapping("/wiki/attachments/{attachmentId}/delete")
  public String deleteAttachment(@PathVariable UUID attachmentId) {
    UUID tenantId = guard.tenantId();
    WikiAttachment attachment = attachmentService.find(tenantId, attachmentId);
    WikiPage wikiPage = pageService.findById(tenantId, attachment.getPageId());
    guard.requireEdit(wikiPage.getSpaceId());
    UUID pageId = attachment.getPageId();
    attachmentService.delete(tenantId, attachmentId);
    audit("WIKI_ATTACH_DELETE", pageId, attachment.getFilename());
    return "redirect:/wiki/" + pageId;
  }

  /** 업로드 용량 초과(멀티파트 한도)는 500 대신 안내로 되돌린다. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public String attachmentTooLarge(RedirectAttributes redirect) {
    redirect.addFlashAttribute("wikiError", "첨부 용량이 한도(20MB)를 초과했습니다.");
    return "redirect:/wiki";
  }

  /**
   * inline 렌더를 허용할 안전한 이미지 타입인지. 화이트리스트 방식이며 <b>SVG는 제외</b>한다
   * (스크립트 실행 가능). 목록에 없으면 첨부로 내려받게 해 브라우저가 실행하지 않는다.
   */
  private static boolean isInlineSafeImage(String contentType) {
    if (contentType == null) {
      return false;
    }
    String normalized = contentType.toLowerCase(Locale.ROOT).trim();
    int separator = normalized.indexOf(';');
    if (separator >= 0) {
      normalized = normalized.substring(0, separator).trim();
    }
    return INLINE_SAFE_IMAGE_TYPES.contains(normalized);
  }

  private static MediaType mediaType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    try {
      return MediaType.parseMediaType(contentType);
    } catch (RuntimeException invalid) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  /** 특권 행위 기록. 정책(행위자 없으면 미기록 등)은 TenantAuditRecorder에 있다. */
  private void audit(String action, UUID targetId, String message) {
    auditRecorder.record("Wiki", action, targetId, message);
  }
}
