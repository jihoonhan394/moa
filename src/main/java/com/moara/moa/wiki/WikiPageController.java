package com.moara.moa.wiki;

import com.moara.moa.ai.AiException;
import com.moara.moa.ai.AiService;
import com.moara.moa.audit.TenantAuditRecorder;
import com.moara.moa.user.ManagedUserService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 위키 문서 본체 — 작성·조회·수정·삭제와 그 문서에 달리는 것들(라벨·댓글·즐겨찾기·버전 이력).
 * 문서를 담는 공간(폴더)과 권한은 {@link WikiSpaceController}, 첨부는
 * {@link WikiAttachmentController}에 있다.
 */
@Controller
public class WikiPageController {
  private final WikiSpaceService spaceService;
  private final WikiPageService pageService;
  private final WikiTemplateService templateService;
  private final WikiEngagementService engagementService;
  private final WikiAttachmentService attachmentService;
  private final MarkdownService markdownService;
  private final AiService aiService;
  private final ManagedUserService userService;
  private final TenantAuditRecorder auditRecorder;
  private final WikiAccessGuard guard;

  public WikiPageController(
      WikiSpaceService spaceService, WikiPageService pageService,
      WikiTemplateService templateService, WikiEngagementService engagementService,
      WikiAttachmentService attachmentService, MarkdownService markdownService,
      AiService aiService, ManagedUserService userService, TenantAuditRecorder auditRecorder,
      WikiAccessGuard guard) {
    this.spaceService = spaceService;
    this.pageService = pageService;
    this.templateService = templateService;
    this.engagementService = engagementService;
    this.attachmentService = attachmentService;
    this.markdownService = markdownService;
    this.aiService = aiService;
    this.userService = userService;
    this.auditRecorder = auditRecorder;
    this.guard = guard;
  }

  // ── 작성 ─────────────────────────────────────────────────────────────────
  @GetMapping("/wiki/spaces/{spaceId}/new")
  public String newPage(@PathVariable UUID spaceId, Model model) {
    guard.requireEdit(spaceId);
    if (!model.containsAttribute("wikiForm")) {
      model.addAttribute("wikiForm", new WikiPageForm(null, null));
    }
    addNewFormModel(model, spaceId);
    return "wiki/form";
  }

  /** 서식 불러오기: 선택한 서식 본문으로 새 문서 폼을 채운다(저장은 사용자가). */
  @PostMapping("/wiki/spaces/{spaceId}/pages/from-template")
  public String fromTemplate(
      @PathVariable UUID spaceId, @RequestParam UUID templateId, Model model) {
    guard.requireEdit(spaceId);
    WikiTemplate template = templateService.findById(guard.tenantId(), templateId);
    if (!spaceId.equals(template.getSpaceId())) {
      throw new AccessDeniedException("이 공간의 서식이 아닙니다.");
    }
    model.addAttribute("wikiForm", new WikiPageForm(null, template.getContent()));
    addNewFormModel(model, spaceId);
    return "wiki/form";
  }

  /** AI 초안 생성: 주제를 받아 마크다운 초안을 만들어 새 문서 폼에 채운다(저장은 사용자가). */
  @PostMapping("/wiki/spaces/{spaceId}/ai-draft")
  public String aiDraft(
      @PathVariable UUID spaceId, @RequestParam String topic, Model model,
      RedirectAttributes redirect) {
    guard.requireEdit(spaceId);
    try {
      String prompt = "다음 주제로 사내 위키 문서를 한국어 마크다운으로 작성해줘. 제목(#), 개요, 단계별 설명,"
          + " 주의사항을 포함하고 표가 유용하면 사용해줘. 코드블록이 필요하면 넣어줘. 주제: " + topic;
      String draft = aiService.generate(guard.tenantId(), prompt);
      audit("WIKI_AI_DRAFT", spaceId, "topic=" + topic);
      model.addAttribute("wikiForm", new WikiPageForm(topic, draft));
      addNewFormModel(model, spaceId);
      return "wiki/form";
    } catch (AiException exception) {
      redirect.addFlashAttribute("wikiError", exception.getMessage());
      return "redirect:/wiki/spaces/" + spaceId + "/new";
    }
  }

  @PostMapping("/wiki/spaces/{spaceId}/pages")
  public String createPage(
      @PathVariable UUID spaceId, @Valid @ModelAttribute("wikiForm") WikiPageForm form,
      BindingResult binding, Model model) {
    guard.requireEdit(spaceId);
    if (binding.hasErrors()) {
      // 폼 재표시에도 정상 경로와 같은 모델을 채운다 — aiConfigured/templates가 없으면
      // 템플릿이 SpringEL 평가에 실패해 검증 오류 대신 깨진 화면이 나간다.
      addNewFormModel(model, spaceId);
      return "wiki/form";
    }
    WikiPage created = pageService.create(guard.tenantId(), spaceId, guard.userId(), form);
    audit("WIKI_CREATE", created.getId(), created.getTitle());
    return "redirect:/wiki/" + created.getId();
  }

  // ── 조회 ─────────────────────────────────────────────────────────────────
  @GetMapping("/wiki/{id}")
  public String view(@PathVariable UUID id, Model model) {
    UUID tenantId = guard.tenantId();
    WikiPage wikiPage = pageService.findById(tenantId, id);
    guard.requireView(wikiPage.getSpaceId());
    Map<UUID, String> names = userService.namesByTenant(tenantId);
    model.addAttribute("wikiPage", wikiPage);
    model.addAttribute("space", spaceService.findById(tenantId, wikiPage.getSpaceId()));
    model.addAttribute("renderedContent", markdownService.toSafeHtml(wikiPage.getContent()));
    model.addAttribute("authorName", wikiPage.getAuthorUserId() == null
        ? "알 수 없음" : names.getOrDefault(wikiPage.getAuthorUserId(), "알 수 없음"));
    model.addAttribute("canEdit", guard.canEdit(wikiPage.getSpaceId()));
    model.addAttribute("canManage", guard.canManage(wikiPage.getSpaceId()));
    model.addAttribute("canDelete", canDeletePage(wikiPage));
    model.addAttribute("labels", engagementService.labels(tenantId, id));
    model.addAttribute("comments", engagementService.comments(tenantId, id));
    model.addAttribute("commentAuthors", names);
    model.addAttribute("isFavorite", engagementService.isFavorite(tenantId, guard.userId(), id));
    model.addAttribute("myUserId", guard.userId());
    model.addAttribute("aiConfigured", aiService.isConfigured(tenantId));
    model.addAttribute("attachments", attachmentService.list(tenantId, id));
    model.addAttribute("page", "wiki");
    return "wiki/view";
  }

  // ── 수정 · 삭제 ──────────────────────────────────────────────────────────
  @GetMapping("/wiki/{id}/edit")
  public String editForm(@PathVariable UUID id, Model model) {
    WikiPage wikiPage = pageService.findById(guard.tenantId(), id);
    guard.requireEdit(wikiPage.getSpaceId());
    if (!model.containsAttribute("wikiForm")) {
      model.addAttribute("wikiForm", new WikiPageForm(wikiPage.getTitle(), wikiPage.getContent()));
    }
    model.addAttribute("space", spaceService.findById(guard.tenantId(), wikiPage.getSpaceId()));
    model.addAttribute("pageId", id);
    model.addAttribute("mode", "edit");
    model.addAttribute("aiConfigured", aiService.isConfigured(guard.tenantId()));
    model.addAttribute("page", "wiki");
    return "wiki/form";
  }

  @PostMapping("/wiki/{id}")
  public String update(
      @PathVariable UUID id, @Valid @ModelAttribute("wikiForm") WikiPageForm form,
      BindingResult binding, Model model) {
    WikiPage wikiPage = pageService.findById(guard.tenantId(), id);
    guard.requireEdit(wikiPage.getSpaceId());
    if (binding.hasErrors()) {
      // createPage와 같은 이유로 aiConfigured/templates를 함께 채운다.
      model.addAttribute("space", spaceService.findById(guard.tenantId(), wikiPage.getSpaceId()));
      model.addAttribute("pageId", id);
      model.addAttribute("mode", "edit");
      model.addAttribute("aiConfigured", aiService.isConfigured(guard.tenantId()));
      model.addAttribute(
          "templates", templateService.findBySpace(guard.tenantId(), wikiPage.getSpaceId()));
      model.addAttribute("page", "wiki");
      return "wiki/form";
    }
    pageService.update(guard.tenantId(), id, guard.userId(), form);
    audit("WIKI_UPDATE", id, form.title());
    return "redirect:/wiki/" + id;
  }

  @PostMapping("/wiki/{id}/delete")
  public String deletePage(@PathVariable UUID id) {
    WikiPage wikiPage = pageService.findById(guard.tenantId(), id);
    if (!canDeletePage(wikiPage)) {
      throw new AccessDeniedException("작성자 또는 공간 관리자만 삭제할 수 있습니다.");
    }
    UUID spaceId = wikiPage.getSpaceId();
    // 첨부 파일 본체까지 정리(FK CASCADE는 DB 메타만 지움)
    attachmentService.deleteAllForPage(guard.tenantId(), id);
    pageService.delete(guard.tenantId(), id);
    audit("WIKI_DELETE", id, wikiPage.getTitle());
    return spaceId != null ? "redirect:/wiki/spaces/" + spaceId : "redirect:/wiki";
  }

  // ── 라벨 · 댓글 · 즐겨찾기 ──────────────────────────────────────────────
  @PostMapping("/wiki/{id}/labels")
  public String addLabel(@PathVariable UUID id, @RequestParam String label) {
    WikiPage wikiPage = pageService.findById(guard.tenantId(), id);
    guard.requireEdit(wikiPage.getSpaceId());
    engagementService.addLabel(guard.tenantId(), id, label);
    return "redirect:/wiki/" + id;
  }

  @PostMapping("/wiki/{id}/labels/{labelId}/delete")
  public String removeLabel(@PathVariable UUID id, @PathVariable UUID labelId) {
    WikiPage wikiPage = pageService.findById(guard.tenantId(), id);
    guard.requireEdit(wikiPage.getSpaceId());
    engagementService.removeLabel(guard.tenantId(), labelId);
    return "redirect:/wiki/" + id;
  }

  @PostMapping("/wiki/{id}/comments")
  public String addComment(@PathVariable UUID id, @RequestParam String content) {
    WikiPage wikiPage = pageService.findById(guard.tenantId(), id);
    guard.requireView(wikiPage.getSpaceId());
    if (content != null && !content.isBlank()) {
      engagementService.addComment(guard.tenantId(), id, guard.userId(), content);
      audit("WIKI_COMMENT", id, null);
    }
    return "redirect:/wiki/" + id;
  }

  @PostMapping("/wiki/{id}/comments/{commentId}/delete")
  public String deleteComment(@PathVariable UUID id, @PathVariable UUID commentId) {
    WikiPage wikiPage = pageService.findById(guard.tenantId(), id);
    guard.requireView(wikiPage.getSpaceId());
    WikiComment comment = engagementService.comment(guard.tenantId(), commentId);
    boolean owner = comment.getAuthorUserId() != null
        && comment.getAuthorUserId().equals(guard.userId());
    if (!owner && !guard.canManage(wikiPage.getSpaceId())) {
      throw new AccessDeniedException("작성자 또는 공간 관리자만 삭제할 수 있습니다.");
    }
    engagementService.deleteComment(guard.tenantId(), commentId);
    return "redirect:/wiki/" + id;
  }

  @PostMapping("/wiki/{id}/favorite")
  public String toggleFavorite(@PathVariable UUID id) {
    WikiPage wikiPage = pageService.findById(guard.tenantId(), id);
    guard.requireView(wikiPage.getSpaceId());
    engagementService.toggleFavorite(guard.tenantId(), guard.userId(), id);
    return "redirect:/wiki/" + id;
  }

  // ── 버전 이력 ────────────────────────────────────────────────────────────
  @GetMapping("/wiki/{id}/history")
  public String history(@PathVariable UUID id, Model model) {
    WikiPage wikiPage = pageService.findById(guard.tenantId(), id);
    guard.requireView(wikiPage.getSpaceId());
    Map<UUID, String> names = userService.namesByTenant(guard.tenantId());
    List<Map<String, Object>> rows = new ArrayList<>();
    for (WikiPageRevision revision : pageService.revisions(guard.tenantId(), id)) {
      Map<String, Object> row = new HashMap<>();
      row.put("revId", revision.getId());
      row.put("title", revision.getTitle());
      row.put("editor", revision.getEditedByUserId() != null
          ? names.getOrDefault(revision.getEditedByUserId(), "알 수 없음") : "알 수 없음");
      row.put("at", revision.getCreatedAt());
      rows.add(row);
    }
    model.addAttribute("wikiPage", wikiPage);
    model.addAttribute("revisions", rows);
    model.addAttribute("canEdit", guard.canEdit(wikiPage.getSpaceId()));
    model.addAttribute("page", "wiki");
    return "wiki/history";
  }

  @GetMapping("/wiki/{id}/history/{revId}")
  public String revisionView(@PathVariable UUID id, @PathVariable UUID revId, Model model) {
    WikiPage wikiPage = pageService.findById(guard.tenantId(), id);
    guard.requireView(wikiPage.getSpaceId());
    WikiPageRevision revision = pageService.revision(guard.tenantId(), revId);
    model.addAttribute("wikiPage", wikiPage);
    model.addAttribute("revision", revision);
    model.addAttribute("renderedContent", markdownService.toSafeHtml(revision.getContent()));
    model.addAttribute("canEdit", guard.canEdit(wikiPage.getSpaceId()));
    model.addAttribute("page", "wiki");
    return "wiki/revision";
  }

  @PostMapping("/wiki/{id}/history/{revId}/restore")
  public String restore(@PathVariable UUID id, @PathVariable UUID revId) {
    WikiPage wikiPage = pageService.findById(guard.tenantId(), id);
    guard.requireEdit(wikiPage.getSpaceId());
    pageService.restore(guard.tenantId(), id, revId, guard.userId());
    audit("WIKI_RESTORE", id, "rev=" + revId);
    return "redirect:/wiki/" + id;
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  /**
   * 새 문서 폼(wiki/form, mode=new)이 필요로 하는 모델. 정상 진입·서식 불러오기·AI 초안·검증 실패
   * 재표시가 전부 같은 템플릿을 쓰므로, 한 곳에서 채워 항목 누락으로 화면이 깨지는 일을 막는다.
   */
  private void addNewFormModel(Model model, UUID spaceId) {
    model.addAttribute("space", spaceService.findById(guard.tenantId(), spaceId));
    model.addAttribute("mode", "new");
    model.addAttribute("aiConfigured", aiService.isConfigured(guard.tenantId()));
    model.addAttribute("templates", templateService.findBySpace(guard.tenantId(), spaceId));
    model.addAttribute("page", "wiki");
  }

  private boolean canDeletePage(WikiPage wikiPage) {
    if (guard.canManage(wikiPage.getSpaceId())) {
      return true;
    }
    return wikiPage.getAuthorUserId() != null
        && wikiPage.getAuthorUserId().equals(guard.userId());
  }

  /** 특권 행위 기록. 정책(행위자 없으면 미기록 등)은 TenantAuditRecorder에 있다. */
  private void audit(String action, UUID targetId, String message) {
    auditRecorder.record("Wiki", action, targetId, message);
  }
}
