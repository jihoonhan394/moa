package com.moara.moa.category;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.security.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 자산/공유자산 카테고리 관리(자산 관리자 전용). 라우트가 /inventory/**·/shared-resources/** 프리픽스
 * 아래라 SecurityConfig의 ASSET_MANAGER 규칙을 상속한다. 자산은 계층(트리), 공유자산은 평면.
 */
@Controller
public class CategoryController {
  private final CategoryService categoryService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public CategoryController(
      CategoryService categoryService, AuditLogService auditLogService, TenantContext tenantContext) {
    this.categoryService = categoryService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  // ── 자산 카테고리(트리) ────────────────────────────────────────
  @GetMapping("/inventory/categories")
  public String assetList(Model model) {
    return renderAsset(model, null);
  }

  @PostMapping("/inventory/categories")
  public String assetCreate(@RequestParam(required = false) UUID parentId, @RequestParam String name,
      Model model) {
    try {
      categoryService.create(tenantContext.currentTenantId(), CategoryDomain.ASSET, name, parentId);
      audit("ASSET_CATEGORY_CREATE", null);
      return "redirect:/inventory/categories";
    } catch (DuplicateCategoryException | IllegalArgumentException exception) {
      return renderAsset(model, exception.getMessage());
    }
  }

  @PostMapping("/inventory/categories/{id}")
  public String assetRename(@PathVariable UUID id, @RequestParam String name, Model model) {
    try {
      categoryService.rename(tenantContext.currentTenantId(), id, name);
      audit("ASSET_CATEGORY_RENAME", id);
      return "redirect:/inventory/categories";
    } catch (DuplicateCategoryException | IllegalArgumentException exception) {
      return renderAsset(model, exception.getMessage());
    }
  }

  @PostMapping("/inventory/categories/{id}/delete")
  public String assetDelete(@PathVariable UUID id, Model model) {
    try {
      categoryService.delete(tenantContext.currentTenantId(), id);
      audit("ASSET_CATEGORY_DELETE", id);
      return "redirect:/inventory/categories";
    } catch (CategoryInUseException exception) {
      return renderAsset(model, exception.getMessage());
    }
  }

  // ── 서버 카테고리(트리) ───────────────────────────────────────
  @GetMapping("/servers/categories")
  public String serverList(Model model) {
    return renderServer(model, null);
  }

  @PostMapping("/servers/categories")
  public String serverCreate(@RequestParam(required = false) UUID parentId, @RequestParam String name,
      Model model) {
    try {
      categoryService.create(tenantContext.currentTenantId(), CategoryDomain.SERVER, name, parentId);
      audit("SERVER_CATEGORY_CREATE", null);
      return "redirect:/servers/categories";
    } catch (DuplicateCategoryException | IllegalArgumentException exception) {
      return renderServer(model, exception.getMessage());
    }
  }

  @PostMapping("/servers/categories/{id}")
  public String serverRename(@PathVariable UUID id, @RequestParam String name, Model model) {
    try {
      categoryService.rename(tenantContext.currentTenantId(), id, name);
      audit("SERVER_CATEGORY_RENAME", id);
      return "redirect:/servers/categories";
    } catch (DuplicateCategoryException | IllegalArgumentException exception) {
      return renderServer(model, exception.getMessage());
    }
  }

  @PostMapping("/servers/categories/{id}/delete")
  public String serverDelete(@PathVariable UUID id, Model model) {
    try {
      categoryService.delete(tenantContext.currentTenantId(), id);
      audit("SERVER_CATEGORY_DELETE", id);
      return "redirect:/servers/categories";
    } catch (CategoryInUseException exception) {
      return renderServer(model, exception.getMessage());
    }
  }

  // ── 솔루션 카테고리(트리) ─────────────────────────────────────
  @GetMapping("/solutions/categories")
  public String solutionList(Model model) {
    return renderSolution(model, null);
  }

  @PostMapping("/solutions/categories")
  public String solutionCreate(@RequestParam(required = false) UUID parentId, @RequestParam String name,
      Model model) {
    try {
      categoryService.create(tenantContext.currentTenantId(), CategoryDomain.SOLUTION, name, parentId);
      audit("SOLUTION_CATEGORY_CREATE", null);
      return "redirect:/solutions/categories";
    } catch (DuplicateCategoryException | IllegalArgumentException exception) {
      return renderSolution(model, exception.getMessage());
    }
  }

  @PostMapping("/solutions/categories/{id}")
  public String solutionRename(@PathVariable UUID id, @RequestParam String name, Model model) {
    try {
      categoryService.rename(tenantContext.currentTenantId(), id, name);
      audit("SOLUTION_CATEGORY_RENAME", id);
      return "redirect:/solutions/categories";
    } catch (DuplicateCategoryException | IllegalArgumentException exception) {
      return renderSolution(model, exception.getMessage());
    }
  }

  @PostMapping("/solutions/categories/{id}/delete")
  public String solutionDelete(@PathVariable UUID id, Model model) {
    try {
      categoryService.delete(tenantContext.currentTenantId(), id);
      audit("SOLUTION_CATEGORY_DELETE", id);
      return "redirect:/solutions/categories";
    } catch (CategoryInUseException exception) {
      return renderSolution(model, exception.getMessage());
    }
  }

  // ── 공유자산 카테고리(평면) ───────────────────────────────────
  @GetMapping("/shared-resources/categories")
  public String sharedList(Model model) {
    return renderShared(model, null);
  }

  @PostMapping("/shared-resources/categories")
  public String sharedCreate(@RequestParam String name, Model model) {
    try {
      categoryService.create(tenantContext.currentTenantId(), CategoryDomain.SHARED_RESOURCE, name, null);
      audit("SHARED_CATEGORY_CREATE", null);
      return "redirect:/shared-resources/categories";
    } catch (DuplicateCategoryException | IllegalArgumentException exception) {
      return renderShared(model, exception.getMessage());
    }
  }

  @PostMapping("/shared-resources/categories/{id}")
  public String sharedRename(@PathVariable UUID id, @RequestParam String name, Model model) {
    try {
      categoryService.rename(tenantContext.currentTenantId(), id, name);
      audit("SHARED_CATEGORY_RENAME", id);
      return "redirect:/shared-resources/categories";
    } catch (DuplicateCategoryException | IllegalArgumentException exception) {
      return renderShared(model, exception.getMessage());
    }
  }

  @PostMapping("/shared-resources/categories/{id}/delete")
  public String sharedDelete(@PathVariable UUID id, Model model) {
    try {
      categoryService.delete(tenantContext.currentTenantId(), id);
      audit("SHARED_CATEGORY_DELETE", id);
      return "redirect:/shared-resources/categories";
    } catch (CategoryInUseException exception) {
      return renderShared(model, exception.getMessage());
    }
  }

  // ── 렌더 ─────────────────────────────────────────────────────
  private String renderAsset(Model model, String error) {
    UUID tenantId = tenantContext.currentTenantId();
    model.addAttribute("nodes", categoryService.assetTree(tenantId));
    model.addAttribute("hierarchical", true);
    model.addAttribute("hierarchicalRootAddable", false);
    model.addAttribute("domainLabel", "자산");
    model.addAttribute("basePath", "/inventory/categories");
    model.addAttribute("backLink", "/inventory");
    model.addAttribute("backLabel", "인벤토리");
    return finish(model, error, "inventory");
  }

  private String renderServer(Model model, String error) {
    UUID tenantId = tenantContext.currentTenantId();
    model.addAttribute("nodes", categoryService.tree(tenantId, CategoryDomain.SERVER));
    model.addAttribute("hierarchical", true);
    model.addAttribute("hierarchicalRootAddable", true);
    model.addAttribute("domainLabel", "서버");
    model.addAttribute("basePath", "/servers/categories");
    model.addAttribute("backLink", "/servers");
    model.addAttribute("backLabel", "서버");
    return finish(model, error, "servers");
  }

  private String renderSolution(Model model, String error) {
    UUID tenantId = tenantContext.currentTenantId();
    model.addAttribute("nodes", categoryService.tree(tenantId, CategoryDomain.SOLUTION));
    model.addAttribute("hierarchical", true);
    model.addAttribute("hierarchicalRootAddable", true);
    model.addAttribute("domainLabel", "솔루션");
    model.addAttribute("basePath", "/solutions/categories");
    model.addAttribute("backLink", "/solutions");
    model.addAttribute("backLabel", "솔루션");
    return finish(model, error, "solutions");
  }

  private String renderShared(Model model, String error) {
    UUID tenantId = tenantContext.currentTenantId();
    List<CategoryService.CategoryNode> nodes = categoryService
        .list(tenantId, CategoryDomain.SHARED_RESOURCE).stream()
        .map(c -> new CategoryService.CategoryNode(
            c.getId(), c.getName(), null, null, 0, c.getName(), false))
        .toList();
    model.addAttribute("nodes", nodes);
    model.addAttribute("hierarchical", false);
    model.addAttribute("domainLabel", "공유자산");
    model.addAttribute("basePath", "/shared-resources/categories");
    model.addAttribute("backLink", "/shared-resources");
    model.addAttribute("backLabel", "공유자산");
    return finish(model, error, "shared-resources");
  }

  private String finish(Model model, String error, String currentMenu) {
    if (error != null) {
      model.addAttribute("error", error);
    }
    model.addAttribute("page", currentMenu);
    model.addAttribute("pageTitle", model.getAttribute("domainLabel") + " 카테고리");
    model.addAttribute("projectName", "MOA");
    return "categories/list";
  }

  private void audit(String action, UUID targetId) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "Category", targetId,
          AuditResult.SUCCESS, null);
    }
  }
}
