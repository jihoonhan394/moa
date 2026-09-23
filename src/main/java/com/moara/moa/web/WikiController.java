package com.moara.moa.web;

import com.moara.moa.ai.AiException;
import com.moara.moa.ai.AiService;
import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserRole;
import com.moara.moa.wiki.MarkdownService;
import com.moara.moa.wiki.WikiAccessLevel;
import com.moara.moa.wiki.WikiAccessService;
import com.moara.moa.wiki.WikiComment;
import com.moara.moa.wiki.WikiEngagementService;
import com.moara.moa.wiki.WikiPage;
import com.moara.moa.wiki.WikiPageForm;
import com.moara.moa.wiki.WikiPageRevision;
import com.moara.moa.wiki.WikiPageService;
import com.moara.moa.wiki.WikiSpace;
import com.moara.moa.wiki.WikiSpaceForm;
import com.moara.moa.wiki.WikiSpaceNotEmptyException;
import com.moara.moa.wiki.WikiSpacePermission;
import com.moara.moa.wiki.WikiSpaceService;
import com.moara.moa.wiki.WikiSubjectType;
import com.moara.moa.wiki.WikiTemplate;
import com.moara.moa.wiki.WikiTemplateForm;
import com.moara.moa.wiki.WikiTemplateService;
import com.moara.moa.wiki.WikiAttachment;
import com.moara.moa.wiki.WikiAttachmentService;
import jakarta.validation.Valid;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 위키 v2. 공간(폴더) 계층 + 공간 단위 권한(상속). 기관 관리자는 최상위 폴더 생성 + 부서장(MANAGE) 지정,
 * 부서장은 하위 폴더 생성·권한 부여, 편집 권한자는 문서 작성/편집. 내용은 마크다운→새니타이즈 렌더.
 */
@Controller
public class WikiController {
  /** inline 렌더를 허용하는 이미지 타입(화이트리스트). SVG는 스크립트 실행이 가능해 제외한다. */
  private static final Set<String> INLINE_SAFE_IMAGE_TYPES = Set.of(
      "image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp");

  // AI 프롬프트에 싣는 본문/선택 텍스트 상한(초대형 문서로 인한 비용·타임아웃 방지).
  private static final int MAX_AI_INPUT = 8000;
  private final WikiSpaceService spaceService;
  private final WikiPageService pageService;
  private final WikiAccessService accessService;
  private final MarkdownService markdownService;
  private final AiService aiService;
  private final WikiTemplateService templateService;
  private final WikiEngagementService engagementService;
  private final WikiAttachmentService attachmentService;
  private final ManagedUserService userService;
  private final AccessGroupService groupService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public WikiController(
      WikiSpaceService spaceService, WikiPageService pageService, WikiAccessService accessService,
      MarkdownService markdownService, AiService aiService, WikiTemplateService templateService,
      WikiEngagementService engagementService, WikiAttachmentService attachmentService,
      ManagedUserService userService, AccessGroupService groupService,
      AuditLogService auditLogService, TenantContext tenantContext) {
    this.spaceService = spaceService;
    this.pageService = pageService;
    this.accessService = accessService;
    this.markdownService = markdownService;
    this.aiService = aiService;
    this.templateService = templateService;
    this.engagementService = engagementService;
    this.attachmentService = attachmentService;
    this.userService = userService;
    this.groupService = groupService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  // ── 홈: 접근 가능한 공간의 진입점(루트) 목록 ──────────────────────────────
  @GetMapping("/wiki")
  public String home(Model model) {
    UUID tenantId = tid();
    List<WikiSpace> accessible = accessService.accessibleSpaces(tenantId, uid(), tenantAdmin());
    Set<UUID> accessibleIds = new HashSet<>();
    accessible.forEach(s -> accessibleIds.add(s.getId()));
    // 루트 = 부모가 없거나 부모가 접근 불가한 접근 가능 공간(고아 서브폴더도 진입점으로 노출).
    List<WikiSpace> roots = accessible.stream()
        .filter(s -> s.getParentId() == null || !accessibleIds.contains(s.getParentId()))
        .toList();
    model.addAttribute("spaces", roots);
    model.addAttribute("canCreateTop", tenantAdmin());
    if (tenantAdmin()) {
      model.addAttribute("tenantUsers", userService.findByTenant(tenantId));
    }
    if (!model.containsAttribute("spaceForm")) {
      model.addAttribute("spaceForm", new WikiSpaceForm(null, null));
    }
    // 즐겨찾기(접근 가능한 것만).
    Set<UUID> favoriteIds = engagementService.favoritePageIds(tenantId, uid());
    List<Map<String, Object>> favorites = new ArrayList<>();
    if (!favoriteIds.isEmpty()) {
      Map<UUID, String> spaceNames = new HashMap<>();
      spaceService.findAll(tenantId).forEach(s -> spaceNames.put(s.getId(), s.getName()));
      for (UUID favPageId : favoriteIds) {
        try {
          WikiPage favPage = pageService.findById(tenantId, favPageId);
          if (favPage.getSpaceId() != null
              && accessService.canView(tenantId, uid(), tenantAdmin(), favPage.getSpaceId())) {
            Map<String, Object> row = new HashMap<>();
            row.put("pageId", favPage.getId());
            row.put("title", favPage.getTitle());
            row.put("spaceName", spaceNames.getOrDefault(favPage.getSpaceId(), "(공간)"));
            favorites.add(row);
          }
        } catch (RuntimeException ignored) {
          // 삭제된 페이지 등은 건너뜀
        }
      }
    }
    model.addAttribute("favorites", favorites);
    model.addAttribute("page", "wiki");
    return "wiki/home";
  }

  // ── 검색(접근 가능한 공간의 문서만) ─────────────────────────────────────
  @GetMapping("/wiki/search")
  public String search(@RequestParam(required = false) String q, Model model) {
    UUID tenantId = tid();
    List<Map<String, Object>> results = new ArrayList<>();
    if (q != null && !q.isBlank()) {
      Map<UUID, String> spaceNames = new HashMap<>();
      spaceService.findAll(tenantId).forEach(s -> spaceNames.put(s.getId(), s.getName()));
      for (WikiPage wikiPage : pageService.search(tenantId, q)) {
        if (wikiPage.getSpaceId() != null
            && accessService.canView(tenantId, uid(), tenantAdmin(), wikiPage.getSpaceId())) {
          Map<String, Object> row = new HashMap<>();
          row.put("pageId", wikiPage.getId());
          row.put("title", wikiPage.getTitle());
          row.put("spaceName", spaceNames.getOrDefault(wikiPage.getSpaceId(), "(공간)"));
          row.put("snippet", snippet(wikiPage.getContent()));
          results.add(row);
        }
      }
    }
    model.addAttribute("q", q);
    model.addAttribute("results", results);
    model.addAttribute("page", "wiki");
    return "wiki/search";
  }

  private String snippet(String content) {
    if (content == null) {
      return "";
    }
    String flat = content.replaceAll("\\s+", " ").trim();
    return flat.length() > 160 ? flat.substring(0, 160) + "…" : flat;
  }

  // ── 공간 보기: 하위 폴더 + 페이지 ────────────────────────────────────────
  @GetMapping("/wiki/spaces/{spaceId}")
  public String space(@PathVariable UUID spaceId, Model model) {
    UUID tenantId = tid();
    WikiSpace space = spaceService.findById(tenantId, spaceId);
    requireView(spaceId);
    boolean canEdit = accessService.canEdit(tenantId, uid(), tenantAdmin(), spaceId);
    boolean canManage = accessService.canManage(tenantId, uid(), tenantAdmin(), spaceId);
    // 접근 가능한 하위 폴더.
    List<WikiSpace> children = spaceService.findAll(tenantId).stream()
        .filter(s -> spaceId.equals(s.getParentId()))
        .filter(s -> accessService.canView(tenantId, uid(), tenantAdmin(), s.getId()))
        .toList();
    model.addAttribute("space", space);
    model.addAttribute("children", children);
    model.addAttribute("pages", pageService.findBySpace(tenantId, spaceId));
    model.addAttribute("ancestors", accessibleAncestors(tenantId, space));
    model.addAttribute("canEdit", canEdit);
    model.addAttribute("canManage", canManage);
    model.addAttribute("viewers", viewerRows(tenantId, spaceId)); // 열람자 배지
    if (!model.containsAttribute("spaceForm")) {
      model.addAttribute("spaceForm", new WikiSpaceForm(null, null));
    }
    model.addAttribute("page", "wiki");
    return "wiki/space";
  }

  @PostMapping("/wiki/spaces")
  public String createTop(
      @Valid @ModelAttribute("spaceForm") WikiSpaceForm form, BindingResult binding,
      @RequestParam(required = false) UUID ownerUserId, RedirectAttributes redirect) {
    if (!tenantAdmin()) {
      throw new AccessDeniedException("최상위 폴더는 기관 관리자만 만들 수 있습니다.");
    }
    if (binding.hasErrors()) {
      redirect.addFlashAttribute("wikiError", "폴더 이름을 확인하세요.");
      return "redirect:/wiki";
    }
    WikiSpace created = spaceService.create(tid(), null, form);
    // 부서장(오너) 지정: MANAGE 부여.
    if (ownerUserId != null) {
      spaceService.grant(tid(), created.getId(), WikiSubjectType.USER, ownerUserId, WikiAccessLevel.MANAGE);
    }
    audit("WIKI_SPACE_CREATE", created.getId(), created.getName());
    return "redirect:/wiki/spaces/" + created.getId();
  }

  @PostMapping("/wiki/spaces/{parentId}/subfolders")
  public String createSub(
      @PathVariable UUID parentId, @Valid @ModelAttribute("spaceForm") WikiSpaceForm form,
      BindingResult binding, RedirectAttributes redirect) {
    requireManage(parentId); // 부서장(또는 기관 관리자)만 하위 폴더 생성
    if (binding.hasErrors()) {
      redirect.addFlashAttribute("wikiError", "폴더 이름을 확인하세요.");
      return "redirect:/wiki/spaces/" + parentId;
    }
    WikiSpace created = spaceService.create(tid(), parentId, form);
    audit("WIKI_SUBFOLDER_CREATE", created.getId(), created.getName());
    return "redirect:/wiki/spaces/" + created.getId();
  }

  @PostMapping("/wiki/spaces/{spaceId}/delete")
  public String deleteSpace(@PathVariable UUID spaceId, RedirectAttributes redirect) {
    requireManage(spaceId);
    WikiSpace space = spaceService.findById(tid(), spaceId);
    UUID parentId = space.getParentId();
    try {
      spaceService.delete(tid(), spaceId);
      audit("WIKI_SPACE_DELETE", spaceId, space.getName());
    } catch (WikiSpaceNotEmptyException exception) {
      redirect.addFlashAttribute("wikiError", "페이지가 있는 폴더는 삭제할 수 없습니다.");
      return "redirect:/wiki/spaces/" + spaceId;
    }
    return parentId != null ? "redirect:/wiki/spaces/" + parentId : "redirect:/wiki";
  }

  // ── 권한 관리(부서장/기관 관리자) ────────────────────────────────────────
  @GetMapping("/wiki/spaces/{spaceId}/permissions")
  public String permissions(@PathVariable UUID spaceId, Model model) {
    requireManage(spaceId);
    UUID tenantId = tid();
    model.addAttribute("space", spaceService.findById(tenantId, spaceId));
    model.addAttribute("viewers", viewerRows(tenantId, spaceId));
    model.addAttribute("groups", groupService.findAll(tenantId));
    model.addAttribute("tenantUsers", userService.findByTenant(tenantId));
    model.addAttribute("levels", WikiAccessLevel.values());
    model.addAttribute("page", "wiki");
    return "wiki/permissions";
  }

  @PostMapping("/wiki/spaces/{spaceId}/permissions")
  public String grant(
      @PathVariable UUID spaceId, @RequestParam WikiSubjectType subjectType,
      @RequestParam(required = false) UUID subjectId, @RequestParam WikiAccessLevel level,
      RedirectAttributes redirect) {
    requireManage(spaceId);
    if (subjectType != WikiSubjectType.ALL && subjectId == null) {
      redirect.addFlashAttribute("wikiError", "대상(그룹/사용자)을 선택하세요.");
      return "redirect:/wiki/spaces/" + spaceId + "/permissions";
    }
    spaceService.grant(tid(), spaceId, subjectType, subjectId, level);
    audit("WIKI_GRANT", spaceId, subjectType + " " + level);
    return "redirect:/wiki/spaces/" + spaceId + "/permissions";
  }

  @PostMapping("/wiki/spaces/{spaceId}/permissions/{permId}/delete")
  public String revoke(@PathVariable UUID spaceId, @PathVariable UUID permId) {
    requireManage(spaceId);
    spaceService.revoke(tid(), permId);
    audit("WIKI_REVOKE", spaceId, "perm=" + permId);
    return "redirect:/wiki/spaces/" + spaceId + "/permissions";
  }

  // ── 페이지 ───────────────────────────────────────────────────────────────
  @GetMapping("/wiki/spaces/{spaceId}/new")
  public String newPage(@PathVariable UUID spaceId, Model model) {
    requireEdit(spaceId);
    model.addAttribute("space", spaceService.findById(tid(), spaceId));
    if (!model.containsAttribute("wikiForm")) {
      model.addAttribute("wikiForm", new WikiPageForm(null, null));
    }
    model.addAttribute("mode", "new");
    model.addAttribute("aiConfigured", aiService.isConfigured(tid()));
    model.addAttribute("templates", templateService.findBySpace(tid(), spaceId));
    model.addAttribute("page", "wiki");
    return "wiki/form";
  }

  // ── 서식(템플릿) ─────────────────────────────────────────────────────────
  @GetMapping("/wiki/spaces/{spaceId}/templates")
  public String templates(@PathVariable UUID spaceId, Model model) {
    requireManage(spaceId);
    model.addAttribute("space", spaceService.findById(tid(), spaceId));
    model.addAttribute("templates", templateService.findBySpace(tid(), spaceId));
    if (!model.containsAttribute("templateForm")) {
      model.addAttribute("templateForm", new WikiTemplateForm(null, null));
    }
    model.addAttribute("page", "wiki");
    return "wiki/templates";
  }

  @PostMapping("/wiki/spaces/{spaceId}/templates")
  public String createTemplate(
      @PathVariable UUID spaceId, @Valid @ModelAttribute("templateForm") WikiTemplateForm form,
      BindingResult binding, Model model) {
    requireManage(spaceId);
    if (binding.hasErrors()) {
      model.addAttribute("space", spaceService.findById(tid(), spaceId));
      model.addAttribute("templates", templateService.findBySpace(tid(), spaceId));
      model.addAttribute("page", "wiki");
      return "wiki/templates";
    }
    WikiTemplate created = templateService.create(tid(), spaceId, uid(), form);
    audit("WIKI_TEMPLATE_CREATE", created.getId(), created.getName());
    return "redirect:/wiki/spaces/" + spaceId + "/templates";
  }

  @PostMapping("/wiki/spaces/{spaceId}/templates/{templateId}/delete")
  public String deleteTemplate(@PathVariable UUID spaceId, @PathVariable UUID templateId) {
    requireManage(spaceId);
    templateService.delete(tid(), templateId);
    audit("WIKI_TEMPLATE_DELETE", templateId, null);
    return "redirect:/wiki/spaces/" + spaceId + "/templates";
  }

  /** 서식 불러오기: 선택한 서식 본문으로 새 문서 폼을 채운다(저장은 사용자가). */
  @PostMapping("/wiki/spaces/{spaceId}/pages/from-template")
  public String fromTemplate(@PathVariable UUID spaceId, @RequestParam UUID templateId, Model model) {
    requireEdit(spaceId);
    WikiTemplate template = templateService.findById(tid(), templateId);
    if (!spaceId.equals(template.getSpaceId())) {
      throw new AccessDeniedException("이 공간의 서식이 아닙니다.");
    }
    model.addAttribute("space", spaceService.findById(tid(), spaceId));
    model.addAttribute("wikiForm", new WikiPageForm(null, template.getContent()));
    model.addAttribute("mode", "new");
    model.addAttribute("aiConfigured", aiService.isConfigured(tid()));
    model.addAttribute("templates", templateService.findBySpace(tid(), spaceId));
    model.addAttribute("page", "wiki");
    return "wiki/form";
  }

  /** AI 초안 생성: 주제를 받아 마크다운 초안을 만들어 새 문서 폼에 채운다(저장은 사용자가). */
  @PostMapping("/wiki/spaces/{spaceId}/ai-draft")
  public String aiDraft(
      @PathVariable UUID spaceId, @RequestParam String topic, Model model, RedirectAttributes redirect) {
    requireEdit(spaceId);
    try {
      String prompt = "다음 주제로 사내 위키 문서를 한국어 마크다운으로 작성해줘. 제목(#), 개요, 단계별 설명,"
          + " 주의사항을 포함하고 표가 유용하면 사용해줘. 코드블록이 필요하면 넣어줘. 주제: " + topic;
      String draft = aiService.generate(tid(), prompt);
      audit("WIKI_AI_DRAFT", spaceId, "topic=" + topic);
      model.addAttribute("space", spaceService.findById(tid(), spaceId));
      model.addAttribute("wikiForm", new WikiPageForm(topic, draft));
      model.addAttribute("mode", "new");
      model.addAttribute("aiConfigured", true);
      model.addAttribute("page", "wiki");
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
    requireEdit(spaceId);
    if (binding.hasErrors()) {
      model.addAttribute("space", spaceService.findById(tid(), spaceId));
      model.addAttribute("mode", "new");
      model.addAttribute("page", "wiki");
      return "wiki/form";
    }
    WikiPage created = pageService.create(tid(), spaceId, uid(), form);
    audit("WIKI_CREATE", created.getId(), created.getTitle());
    return "redirect:/wiki/" + created.getId();
  }

  @GetMapping("/wiki/{id}")
  public String view(@PathVariable UUID id, Model model) {
    UUID tenantId = tid();
    WikiPage wikiPage = pageService.findById(tenantId, id);
    requireView(wikiPage.getSpaceId());
    model.addAttribute("wikiPage", wikiPage);
    model.addAttribute("space", spaceService.findById(tenantId, wikiPage.getSpaceId()));
    model.addAttribute("renderedContent", markdownService.toSafeHtml(wikiPage.getContent()));
    model.addAttribute("authorName", userName(tenantId, wikiPage.getAuthorUserId()));
    model.addAttribute("canEdit", accessService.canEdit(tenantId, uid(), tenantAdmin(), wikiPage.getSpaceId()));
    model.addAttribute("canManage", accessService.canManage(tenantId, uid(), tenantAdmin(), wikiPage.getSpaceId()));
    model.addAttribute("canDelete", canDeletePage(wikiPage));
    model.addAttribute("labels", engagementService.labels(tenantId, id));
    model.addAttribute("comments", engagementService.comments(tenantId, id));
    model.addAttribute("commentAuthors", userNames(tenantId));
    model.addAttribute("isFavorite", engagementService.isFavorite(tenantId, uid(), id));
    model.addAttribute("myUserId", uid());
    model.addAttribute("aiConfigured", aiService.isConfigured(tenantId));
    model.addAttribute("attachments", attachmentService.list(tenantId, id));
    model.addAttribute("page", "wiki");
    return "wiki/view";
  }

  // ── 첨부파일(서버 파일시스템 저장) ─────────────────────────────────────────
  @PostMapping("/wiki/{id}/attachments")
  public String uploadAttachment(
      @PathVariable UUID id, @RequestParam("file") MultipartFile file, RedirectAttributes redirect) {
    UUID tenantId = tid();
    WikiPage wikiPage = pageService.findById(tenantId, id);
    requireEdit(wikiPage.getSpaceId());
    try {
      WikiAttachment saved = attachmentService.store(tenantId, id, uid(), file);
      audit("WIKI_ATTACH", id, saved.getFilename());
    } catch (IllegalArgumentException | UncheckedIOException exception) {
      redirect.addFlashAttribute("wikiError", exception.getMessage());
    }
    return "redirect:/wiki/" + id;
  }

  @GetMapping("/wiki/attachments/{attachmentId}")
  public ResponseEntity<Resource> downloadAttachment(@PathVariable UUID attachmentId) {
    UUID tenantId = tid();
    WikiAttachment attachment = attachmentService.find(tenantId, attachmentId);
    WikiPage wikiPage = pageService.findById(tenantId, attachment.getPageId());
    requireView(wikiPage.getSpaceId());
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
    UUID tenantId = tid();
    WikiAttachment attachment = attachmentService.find(tenantId, attachmentId);
    WikiPage wikiPage = pageService.findById(tenantId, attachment.getPageId());
    requireEdit(wikiPage.getSpaceId());
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

  /** AI 프롬프트에 실을 텍스트를 상한 길이로 자른다(초과 시 생략 표시). */
  private static String clip(String text, int max) {
    if (text == null) {
      return "";
    }
    return text.length() <= max ? text : text.substring(0, max) + " …(이하 생략)";
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

  /**
   * 페이지 AI 요약(Tier 1): 현재 문서 본문을 근거로 3줄 요약을 생성한다. 열람 권한만 있으면 사용 가능하며,
   * 문서에 없는 내용은 지어내지 않도록 지시한다. 비동기 호출용으로 요약 텍스트만 평문 반환한다.
   */
  @PostMapping("/wiki/{id}/summary")
  @ResponseBody
  public String summary(@PathVariable UUID id) {
    UUID tenantId = tid();
    WikiPage wikiPage = pageService.findById(tenantId, id);
    requireView(wikiPage.getSpaceId());
    if (!aiService.isConfigured(tenantId)) {
      return "AI가 설정되지 않았습니다. 기관 관리자가 'AI 설정'에서 제공자·키를 등록하세요.";
    }
    String prompt = "다음 사내 위키 문서를 한국어로 3줄 이내, 핵심만 간결히 요약해줘."
        + " 문서에 없는 내용은 지어내지 말 것.\n\n제목: " + wikiPage.getTitle()
        + "\n\n본문:\n" + clip(wikiPage.getContent(), MAX_AI_INPUT);
    try {
      String result = aiService.generate(tenantId, prompt);
      audit("WIKI_AI_SUMMARY", id, null);
      return result;
    } catch (AiException exception) {
      return "요약 생성에 실패했습니다: " + exception.getMessage();
    }
  }

  /**
   * 에디터 라이브 미리보기: 입력한 마크다운을 저장 시와 <b>동일한 렌더+새니타이즈 파이프라인</b>으로 처리해
   * 안전 HTML을 반환한다. 미리보기와 실제 저장 결과가 항상 일치하고, XSS 방어도 한 곳에서 보장된다.
   */
  @PostMapping("/wiki/preview")
  @ResponseBody
  public String preview(@RequestParam(required = false) String content) {
    return markdownService.toSafeHtml(content);
  }

  /**
   * 인라인 저작 AI(Tier 2): 에디터에서 선택한 텍스트(또는 본문)를 요약/개선/번역/이어쓰기 한다.
   * 결과 텍스트만 평문 반환하고, 삽입·치환은 클라이언트가 한다. 문서 저장이 아니라 작성 보조이므로 상태 변경 없음.
   */
  @PostMapping("/wiki/ai-assist")
  @ResponseBody
  public String aiAssist(@RequestParam String action, @RequestParam(required = false) String text) {
    UUID tenantId = tid();
    if (!aiService.isConfigured(tenantId)) {
      return "AI가 설정되지 않았습니다. 기관 관리자가 'AI 설정'에서 제공자·키를 등록하세요.";
    }
    String input = text == null ? "" : text;
    if (input.isBlank()) {
      return "(내용을 입력하거나 텍스트를 선택한 뒤 사용하세요.)";
    }
    input = clip(input, MAX_AI_INPUT);
    String prompt = switch (action) {
      case "summarize" -> "다음 텍스트를 한국어로 핵심만 간결히 요약해줘. 요약 결과만 출력:\n\n" + input;
      case "improve" -> "다음 텍스트의 문장을 자연스럽게 다듬고 맞춤법을 교정해줘. 의미는 유지하고 결과 문장만 출력:\n\n" + input;
      case "translate" -> "다음 텍스트가 한국어면 영어로, 영어면 한국어로 번역해줘. 번역 결과만 출력:\n\n" + input;
      case "continue" -> "다음 글에 자연스럽게 이어질 다음 문단을 한국어로 작성해줘. 이어질 문단만 출력:\n\n" + input;
      default -> null;
    };
    if (prompt == null) {
      return "(지원하지 않는 작업입니다.)";
    }
    try {
      return aiService.generate(tenantId, prompt);
    } catch (AiException exception) {
      return "AI 처리에 실패했습니다: " + exception.getMessage();
    }
  }

  // ── 라벨 · 댓글 · 즐겨찾기 ──────────────────────────────────────────────
  @PostMapping("/wiki/{id}/labels")
  public String addLabel(@PathVariable UUID id, @RequestParam String label) {
    WikiPage wikiPage = pageService.findById(tid(), id);
    requireEdit(wikiPage.getSpaceId());
    engagementService.addLabel(tid(), id, label);
    return "redirect:/wiki/" + id;
  }

  @PostMapping("/wiki/{id}/labels/{labelId}/delete")
  public String removeLabel(@PathVariable UUID id, @PathVariable UUID labelId) {
    WikiPage wikiPage = pageService.findById(tid(), id);
    requireEdit(wikiPage.getSpaceId());
    engagementService.removeLabel(tid(), labelId);
    return "redirect:/wiki/" + id;
  }

  @PostMapping("/wiki/{id}/comments")
  public String addComment(@PathVariable UUID id, @RequestParam String content) {
    WikiPage wikiPage = pageService.findById(tid(), id);
    requireView(wikiPage.getSpaceId());
    if (content != null && !content.isBlank()) {
      engagementService.addComment(tid(), id, uid(), content);
      audit("WIKI_COMMENT", id, null);
    }
    return "redirect:/wiki/" + id;
  }

  @PostMapping("/wiki/{id}/comments/{commentId}/delete")
  public String deleteComment(@PathVariable UUID id, @PathVariable UUID commentId) {
    WikiPage wikiPage = pageService.findById(tid(), id);
    requireView(wikiPage.getSpaceId());
    WikiComment comment = engagementService.comment(tid(), commentId);
    boolean owner = comment.getAuthorUserId() != null && comment.getAuthorUserId().equals(uid());
    if (!owner && !accessService.canManage(tid(), uid(), tenantAdmin(), wikiPage.getSpaceId())) {
      throw new AccessDeniedException("작성자 또는 공간 관리자만 삭제할 수 있습니다.");
    }
    engagementService.deleteComment(tid(), commentId);
    return "redirect:/wiki/" + id;
  }

  @PostMapping("/wiki/{id}/favorite")
  public String toggleFavorite(@PathVariable UUID id) {
    WikiPage wikiPage = pageService.findById(tid(), id);
    requireView(wikiPage.getSpaceId());
    engagementService.toggleFavorite(tid(), uid(), id);
    return "redirect:/wiki/" + id;
  }

  // ── 버전 이력 ────────────────────────────────────────────────────────────
  @GetMapping("/wiki/{id}/history")
  public String history(@PathVariable UUID id, Model model) {
    WikiPage wikiPage = pageService.findById(tid(), id);
    requireView(wikiPage.getSpaceId());
    Map<UUID, String> names = userNames(tid());
    List<Map<String, Object>> rows = new ArrayList<>();
    for (WikiPageRevision revision : pageService.revisions(tid(), id)) {
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
    model.addAttribute("canEdit", accessService.canEdit(tid(), uid(), tenantAdmin(), wikiPage.getSpaceId()));
    model.addAttribute("page", "wiki");
    return "wiki/history";
  }

  @GetMapping("/wiki/{id}/history/{revId}")
  public String revisionView(@PathVariable UUID id, @PathVariable UUID revId, Model model) {
    WikiPage wikiPage = pageService.findById(tid(), id);
    requireView(wikiPage.getSpaceId());
    WikiPageRevision revision = pageService.revision(tid(), revId);
    model.addAttribute("wikiPage", wikiPage);
    model.addAttribute("revision", revision);
    model.addAttribute("renderedContent", markdownService.toSafeHtml(revision.getContent()));
    model.addAttribute("canEdit", accessService.canEdit(tid(), uid(), tenantAdmin(), wikiPage.getSpaceId()));
    model.addAttribute("page", "wiki");
    return "wiki/revision";
  }

  @PostMapping("/wiki/{id}/history/{revId}/restore")
  public String restore(@PathVariable UUID id, @PathVariable UUID revId) {
    WikiPage wikiPage = pageService.findById(tid(), id);
    requireEdit(wikiPage.getSpaceId());
    pageService.restore(tid(), id, revId, uid());
    audit("WIKI_RESTORE", id, "rev=" + revId);
    return "redirect:/wiki/" + id;
  }

  @GetMapping("/wiki/{id}/edit")
  public String editForm(@PathVariable UUID id, Model model) {
    WikiPage wikiPage = pageService.findById(tid(), id);
    requireEdit(wikiPage.getSpaceId());
    if (!model.containsAttribute("wikiForm")) {
      model.addAttribute("wikiForm", new WikiPageForm(wikiPage.getTitle(), wikiPage.getContent()));
    }
    model.addAttribute("space", spaceService.findById(tid(), wikiPage.getSpaceId()));
    model.addAttribute("pageId", id);
    model.addAttribute("mode", "edit");
    model.addAttribute("aiConfigured", aiService.isConfigured(tid()));
    model.addAttribute("page", "wiki");
    return "wiki/form";
  }

  @PostMapping("/wiki/{id}")
  public String update(
      @PathVariable UUID id, @Valid @ModelAttribute("wikiForm") WikiPageForm form,
      BindingResult binding, Model model) {
    WikiPage wikiPage = pageService.findById(tid(), id);
    requireEdit(wikiPage.getSpaceId());
    if (binding.hasErrors()) {
      model.addAttribute("space", spaceService.findById(tid(), wikiPage.getSpaceId()));
      model.addAttribute("pageId", id);
      model.addAttribute("mode", "edit");
      model.addAttribute("page", "wiki");
      return "wiki/form";
    }
    pageService.update(tid(), id, uid(), form);
    audit("WIKI_UPDATE", id, form.title());
    return "redirect:/wiki/" + id;
  }

  @PostMapping("/wiki/{id}/delete")
  public String deletePage(@PathVariable UUID id) {
    WikiPage wikiPage = pageService.findById(tid(), id);
    if (!canDeletePage(wikiPage)) {
      throw new AccessDeniedException("작성자 또는 공간 관리자만 삭제할 수 있습니다.");
    }
    UUID spaceId = wikiPage.getSpaceId();
    attachmentService.deleteAllForPage(tid(), id); // 첨부 파일 본체까지 정리(FK CASCADE는 DB 메타만 지움)
    pageService.delete(tid(), id);
    audit("WIKI_DELETE", id, wikiPage.getTitle());
    return spaceId != null ? "redirect:/wiki/spaces/" + spaceId : "redirect:/wiki";
  }

  // ── helpers ──────────────────────────────────────────────────────────────
  private boolean canDeletePage(WikiPage wikiPage) {
    if (accessService.canManage(tid(), uid(), tenantAdmin(), wikiPage.getSpaceId())) {
      return true;
    }
    return wikiPage.getAuthorUserId() != null && wikiPage.getAuthorUserId().equals(uid());
  }

  /** 공간의 직접 부여된 권한을 표시용 행으로(대상 이름 + 수준). 열람자 배지에 쓴다. */
  private List<Map<String, Object>> viewerRows(UUID tenantId, UUID spaceId) {
    Map<UUID, String> groupNames = new HashMap<>();
    for (AccessGroup g : groupService.findAll(tenantId)) {
      groupNames.put(g.getId(), g.getName());
    }
    Map<UUID, String> userNames = userNames(tenantId);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (WikiSpacePermission perm : spaceService.permissions(tenantId, spaceId)) {
      String subject = switch (perm.getSubjectType()) {
        case ALL -> "전체(전 직원)";
        case GROUP -> "부서: " + groupNames.getOrDefault(perm.getSubjectId(), "(삭제된 그룹)");
        case USER -> "사용자: " + userNames.getOrDefault(perm.getSubjectId(), "(알 수 없음)");
      };
      Map<String, Object> row = new HashMap<>();
      row.put("permId", perm.getId());
      row.put("subject", subject);
      row.put("level", perm.getAccessLevel().getLabel());
      rows.add(row);
    }
    return rows;
  }

  /** 접근 가능한 조상 폴더 경로(빵부스러기). */
  private List<WikiSpace> accessibleAncestors(UUID tenantId, WikiSpace space) {
    List<WikiSpace> chain = new ArrayList<>();
    Map<UUID, WikiSpace> byId = new HashMap<>();
    spaceService.findAll(tenantId).forEach(s -> byId.put(s.getId(), s));
    UUID cur = space.getParentId();
    Set<UUID> seen = new HashSet<>();
    while (cur != null && seen.add(cur)) {
      WikiSpace parent = byId.get(cur);
      if (parent == null || !accessService.canView(tenantId, uid(), tenantAdmin(), parent.getId())) {
        break;
      }
      chain.add(0, parent);
      cur = parent.getParentId();
    }
    return chain;
  }

  private Map<UUID, String> userNames(UUID tenantId) {
    Map<UUID, String> names = new HashMap<>();
    for (ManagedUser u : userService.findByTenant(tenantId)) {
      names.put(u.getId(), u.getName());
    }
    return names;
  }

  private String userName(UUID tenantId, UUID userId) {
    return userId == null ? "알 수 없음" : userNames(tenantId).getOrDefault(userId, "알 수 없음");
  }

  private boolean tenantAdmin() {
    MoaUserDetails user = tenantContext.currentUser();
    return user != null && user.hasRole(UserRole.TENANT_ADMIN);
  }

  private UUID tid() { return tenantContext.currentTenantId(); }

  private UUID uid() { return tenantContext.currentUserId(); }

  private void requireView(UUID spaceId) {
    if (!accessService.canView(tid(), uid(), tenantAdmin(), spaceId)) {
      throw new AccessDeniedException("이 위키 공간을 볼 권한이 없습니다.");
    }
  }

  private void requireEdit(UUID spaceId) {
    if (!accessService.canEdit(tid(), uid(), tenantAdmin(), spaceId)) {
      throw new AccessDeniedException("이 위키 공간에 편집 권한이 없습니다.");
    }
  }

  private void requireManage(UUID spaceId) {
    if (!accessService.canManage(tid(), uid(), tenantAdmin(), spaceId)) {
      throw new AccessDeniedException("이 위키 공간을 관리할 권한이 없습니다.");
    }
  }

  private void audit(String action, UUID targetId, String message) {
    UUID actorId = uid();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tid(), actorId, action, "Wiki", targetId, AuditResult.SUCCESS, message);
    }
  }
}
