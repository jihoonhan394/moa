package com.moara.moa.web;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.onboarding.OnboardingItemType;
import com.moara.moa.onboarding.OnboardingService;
import com.moara.moa.onboarding.OnboardingTemplate;
import com.moara.moa.security.TenantContext;
import com.moara.moa.solution.ManagedSolutionService;
import com.moara.moa.wiki.WikiSpaceService;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 입사 온보딩 <b>템플릿 구성</b>(자원을 아는 인프라/자산 관리자 전용). 실제 <b>적용</b>은 부서장이
 * /team-onboarding에서 한다(구성과 적용의 분리). 라우팅 권한은 SecurityConfig(/onboarding/** →
 * INFRA_MANAGER·ASSET_MANAGER)에서 강제한다.
 */
@Controller
public class OnboardingController {
  private final OnboardingService onboardingService;
  private final ManagedSolutionService solutionService;
  private final WikiSpaceService wikiSpaceService;
  private final TenantContext tenantContext;
  private final AuditLogService auditLogService;

  public OnboardingController(
      OnboardingService onboardingService, ManagedSolutionService solutionService,
      WikiSpaceService wikiSpaceService, TenantContext tenantContext,
      AuditLogService auditLogService) {
    this.onboardingService = onboardingService;
    this.solutionService = solutionService;
    this.wikiSpaceService = wikiSpaceService;
    this.tenantContext = tenantContext;
    this.auditLogService = auditLogService;
  }

  @GetMapping("/onboarding")
  public String index(@RequestParam(required = false) UUID template, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    model.addAttribute("templates", onboardingService.templates(tenantId));
    model.addAttribute("itemTypes", OnboardingItemType.values());
    model.addAttribute("solutions", solutionService.findAll(tenantId));
    model.addAttribute("spaces", wikiSpaceService.findAll(tenantId));
    if (template != null) {
      OnboardingTemplate selected = onboardingService.template(tenantId, template);
      model.addAttribute("selected", selected);
      model.addAttribute("items", onboardingService.items(selected.getId()));
    }
    model.addAttribute("page", "onboarding");
    return "onboarding/templates";
  }

  @PostMapping("/onboarding/templates")
  public String create(@RequestParam String name) {
    OnboardingTemplate created = onboardingService.createTemplate(tenantContext.currentTenantId(), name);
    audit("ONBOARDING_TEMPLATE_CREATE", created.getId(), name);
    return "redirect:/onboarding?template=" + created.getId();
  }

  @PostMapping("/onboarding/templates/{id}/items")
  public String addItem(
      @PathVariable UUID id,
      @RequestParam OnboardingItemType type,
      @RequestParam(required = false) UUID refId,
      @RequestParam(required = false) String label) {
    onboardingService.addItem(tenantContext.currentTenantId(), id, type, refId, label);
    audit("ONBOARDING_ITEM_ADD", id, "유형=" + type + (label == null || label.isBlank() ? "" : ", 라벨=" + label));
    return "redirect:/onboarding?template=" + id;
  }

  @PostMapping("/onboarding/templates/{id}/items/{itemId}/delete")
  public String removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
    onboardingService.removeItem(tenantContext.currentTenantId(), id, itemId);
    audit("ONBOARDING_ITEM_REMOVE", itemId, null);
    return "redirect:/onboarding?template=" + id;
  }

  @PostMapping("/onboarding/templates/{id}/delete")
  public String deleteTemplate(@PathVariable UUID id) {
    onboardingService.deleteTemplate(tenantContext.currentTenantId(), id);
    audit("ONBOARDING_TEMPLATE_DELETE", id, null);
    return "redirect:/onboarding";
  }

  private void audit(String action, UUID targetId, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "OnboardingTemplate", targetId,
          AuditResult.SUCCESS, message);
    }
  }
}
