package com.moara.moa.wiki;

import com.moara.moa.audit.TenantAuditRecorder;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.user.ManagedUserService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * 위키 공간(폴더) 계층과 그 위의 권한·서식. 기관 관리자는 최상위 폴더 생성 + 부서장(MANAGE) 지정,
 * 부서장은 하위 폴더 생성·권한 부여를 한다. 문서 본체는 {@link WikiPageController}에 있다.
 */
@Controller
public class WikiSpaceController {
  private final WikiSpaceService spaceService;
  private final WikiPageService pageService;
  private final WikiTemplateService templateService;
  private final ManagedUserService userService;
  private final AccessGroupService groupService;
  private final TenantAuditRecorder auditRecorder;
  private final WikiAccessGuard guard;

  public WikiSpaceController(
      WikiSpaceService spaceService, WikiPageService pageService,
      WikiTemplateService templateService, ManagedUserService userService,
      AccessGroupService groupService, TenantAuditRecorder auditRecorder, WikiAccessGuard guard) {
    this.spaceService = spaceService;
    this.pageService = pageService;
    this.templateService = templateService;
    this.userService = userService;
    this.groupService = groupService;
    this.auditRecorder = auditRecorder;
    this.guard = guard;
  }

  // ── 공간 보기: 하위 폴더 + 페이지 ────────────────────────────────────────
  @GetMapping("/wiki/spaces/{spaceId}")
  public String space(@PathVariable UUID spaceId, Model model) {
    UUID tenantId = guard.tenantId();
    WikiSpace space = spaceService.findById(tenantId, spaceId);
    guard.requireView(spaceId);
    // 접근 가능한 하위 폴더.
    List<WikiSpace> children = spaceService.findAll(tenantId).stream()
        .filter(s -> spaceId.equals(s.getParentId()))
        .filter(s -> guard.canView(s.getId()))
        .toList();
    model.addAttribute("space", space);
    model.addAttribute("children", children);
    model.addAttribute("pages", pageService.findBySpace(tenantId, spaceId));
    model.addAttribute("ancestors", accessibleAncestors(tenantId, space));
    model.addAttribute("canEdit", guard.canEdit(spaceId));
    model.addAttribute("canManage", guard.canManage(spaceId));
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
    if (!guard.tenantAdmin()) {
      throw new AccessDeniedException("최상위 폴더는 기관 관리자만 만들 수 있습니다.");
    }
    if (binding.hasErrors()) {
      redirect.addFlashAttribute("wikiError", "폴더 이름을 확인하세요.");
      return "redirect:/wiki";
    }
    WikiSpace created = spaceService.create(guard.tenantId(), null, form);
    // 부서장(오너) 지정: MANAGE 부여.
    if (ownerUserId != null) {
      spaceService.grant(
          guard.tenantId(), created.getId(), WikiSubjectType.USER, ownerUserId,
          WikiAccessLevel.MANAGE);
    }
    audit("WIKI_SPACE_CREATE", created.getId(), created.getName());
    return "redirect:/wiki/spaces/" + created.getId();
  }

  @PostMapping("/wiki/spaces/{parentId}/subfolders")
  public String createSub(
      @PathVariable UUID parentId, @Valid @ModelAttribute("spaceForm") WikiSpaceForm form,
      BindingResult binding, RedirectAttributes redirect) {
    guard.requireManage(parentId); // 부서장(또는 기관 관리자)만 하위 폴더 생성
    if (binding.hasErrors()) {
      redirect.addFlashAttribute("wikiError", "폴더 이름을 확인하세요.");
      return "redirect:/wiki/spaces/" + parentId;
    }
    WikiSpace created = spaceService.create(guard.tenantId(), parentId, form);
    audit("WIKI_SUBFOLDER_CREATE", created.getId(), created.getName());
    return "redirect:/wiki/spaces/" + created.getId();
  }

  @PostMapping("/wiki/spaces/{spaceId}/delete")
  public String deleteSpace(@PathVariable UUID spaceId, RedirectAttributes redirect) {
    guard.requireManage(spaceId);
    WikiSpace space = spaceService.findById(guard.tenantId(), spaceId);
    UUID parentId = space.getParentId();
    try {
      spaceService.delete(guard.tenantId(), spaceId);
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
    guard.requireManage(spaceId);
    UUID tenantId = guard.tenantId();
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
    guard.requireManage(spaceId);
    if (subjectType != WikiSubjectType.ALL && subjectId == null) {
      redirect.addFlashAttribute("wikiError", "대상(그룹/사용자)을 선택하세요.");
      return "redirect:/wiki/spaces/" + spaceId + "/permissions";
    }
    spaceService.grant(guard.tenantId(), spaceId, subjectType, subjectId, level);
    audit("WIKI_GRANT", spaceId, subjectType + " " + level);
    return "redirect:/wiki/spaces/" + spaceId + "/permissions";
  }

  @PostMapping("/wiki/spaces/{spaceId}/permissions/{permId}/delete")
  public String revoke(@PathVariable UUID spaceId, @PathVariable UUID permId) {
    guard.requireManage(spaceId);
    spaceService.revoke(guard.tenantId(), permId);
    audit("WIKI_REVOKE", spaceId, "perm=" + permId);
    return "redirect:/wiki/spaces/" + spaceId + "/permissions";
  }

  // ── 서식(템플릿) ─────────────────────────────────────────────────────────
  @GetMapping("/wiki/spaces/{spaceId}/templates")
  public String templates(@PathVariable UUID spaceId, Model model) {
    guard.requireManage(spaceId);
    model.addAttribute("space", spaceService.findById(guard.tenantId(), spaceId));
    model.addAttribute("templates", templateService.findBySpace(guard.tenantId(), spaceId));
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
    guard.requireManage(spaceId);
    if (binding.hasErrors()) {
      model.addAttribute("space", spaceService.findById(guard.tenantId(), spaceId));
      model.addAttribute("templates", templateService.findBySpace(guard.tenantId(), spaceId));
      model.addAttribute("page", "wiki");
      return "wiki/templates";
    }
    WikiTemplate created =
        templateService.create(guard.tenantId(), spaceId, guard.userId(), form);
    audit("WIKI_TEMPLATE_CREATE", created.getId(), created.getName());
    return "redirect:/wiki/spaces/" + spaceId + "/templates";
  }

  @PostMapping("/wiki/spaces/{spaceId}/templates/{templateId}/delete")
  public String deleteTemplate(@PathVariable UUID spaceId, @PathVariable UUID templateId) {
    guard.requireManage(spaceId);
    templateService.delete(guard.tenantId(), templateId);
    audit("WIKI_TEMPLATE_DELETE", templateId, null);
    return "redirect:/wiki/spaces/" + spaceId + "/templates";
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  /** 공간의 직접 부여된 권한을 표시용 행으로(대상 이름 + 수준). 열람자 배지에 쓴다. */
  private List<Map<String, Object>> viewerRows(UUID tenantId, UUID spaceId) {
    Map<UUID, String> groupNames = new HashMap<>();
    for (AccessGroup g : groupService.findAll(tenantId)) {
      groupNames.put(g.getId(), g.getName());
    }
    Map<UUID, String> userNames = userService.namesByTenant(tenantId);
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
      if (parent == null || !guard.canView(parent.getId())) {
        break;
      }
      chain.add(0, parent);
      cur = parent.getParentId();
    }
    return chain;
  }

  /** 특권 행위 기록. 정책(행위자 없으면 미기록 등)은 TenantAuditRecorder에 있다. */
  private void audit(String action, UUID targetId, String message) {
    auditRecorder.record("Wiki", action, targetId, message);
  }
}
