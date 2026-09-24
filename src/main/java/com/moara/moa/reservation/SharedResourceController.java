package com.moara.moa.reservation;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.category.CategoryDomain;
import com.moara.moa.category.CategoryService;
import com.moara.moa.security.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/** 공유자산(차량·회의실·좌석) 관리 UI(자산 관리자 전용). 등록·수정·삭제. */
@Controller
public class SharedResourceController {
  private final SharedResourceService resourceService;
  private final CategoryService categoryService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public SharedResourceController(
      SharedResourceService resourceService, CategoryService categoryService,
      AuditLogService auditLogService, TenantContext tenantContext) {
    this.resourceService = resourceService;
    this.categoryService = categoryService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/shared-resources")
  public String list(Model model) {
    if (!model.containsAttribute("resourceForm")) {
      model.addAttribute("resourceForm", new SharedResourceForm(null, null, null, null, null, null));
    }
    populate(model);
    return "shared-resources/list";
  }

  @PostMapping("/shared-resources")
  public String create(
      @Valid @ModelAttribute("resourceForm") SharedResourceForm form, BindingResult binding, Model model) {
    if (binding.hasErrors()) {
      populate(model);
      return "shared-resources/list";
    }
    try {
      SharedResource created = resourceService.create(tenantContext.currentTenantId(), form);
      audit("SHARED_RESOURCE_CREATE", created.getId(), created.getName());
      return "redirect:/shared-resources";
    } catch (DuplicateSharedResourceException exception) {
      binding.reject("resource.duplicate", "이미 사용 중인 자산 이름입니다.");
      populate(model);
      return "shared-resources/list";
    }
  }

  @GetMapping("/shared-resources/{id}/edit")
  public String editForm(@PathVariable UUID id, Model model) {
    SharedResource resource = resourceService.findById(tenantContext.currentTenantId(), id);
    model.addAttribute("resourceId", id);
    model.addAttribute("resourceForm", new SharedResourceForm(
        resource.getName(), resource.getCategory(), resource.getLocation(), resource.getCapacity(),
        resource.getStatus(), resource.getDescription()));
    model.addAttribute("categories", categoryNames());
    model.addAttribute("statuses", SharedResourceStatus.values());
    return "shared-resources/form";
  }

  @PostMapping("/shared-resources/{id}")
  public String update(
      @PathVariable UUID id, @Valid @ModelAttribute("resourceForm") SharedResourceForm form,
      BindingResult binding, Model model) {
    if (binding.hasErrors()) {
      model.addAttribute("resourceId", id);
      model.addAttribute("categories", categoryNames());
      model.addAttribute("statuses", SharedResourceStatus.values());
      return "shared-resources/form";
    }
    try {
      resourceService.update(tenantContext.currentTenantId(), id, form);
      audit("SHARED_RESOURCE_UPDATE", id, form.name());
      return "redirect:/shared-resources";
    } catch (DuplicateSharedResourceException exception) {
      binding.reject("resource.duplicate", "이미 사용 중인 자산 이름입니다.");
      model.addAttribute("resourceId", id);
      model.addAttribute("categories", categoryNames());
      model.addAttribute("statuses", SharedResourceStatus.values());
      return "shared-resources/form";
    }
  }

  @PostMapping("/shared-resources/{id}/delete")
  public String delete(@PathVariable UUID id) {
    resourceService.delete(tenantContext.currentTenantId(), id);
    audit("SHARED_RESOURCE_DELETE", id, null);
    return "redirect:/shared-resources";
  }

  private void populate(Model model) {
    model.addAttribute("resources", resourceService.findAll(tenantContext.currentTenantId()));
    model.addAttribute("categories", categoryNames());
    model.addAttribute("statuses", SharedResourceStatus.values());
    model.addAttribute("page", "shared-resources");
    model.addAttribute("pageTitle", "공유자산");
    model.addAttribute("projectName", "MOA");
  }

  private java.util.List<String> categoryNames() {
    return categoryService.names(tenantContext.currentTenantId(), CategoryDomain.SHARED_RESOURCE);
  }

  private void audit(String action, UUID targetId, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "SharedResource", targetId,
          AuditResult.SUCCESS, message);
    }
  }
}
