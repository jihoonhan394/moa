package com.moara.moa.inventory;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.category.CategoryDomain;
import com.moara.moa.category.CategoryService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * 실물·SW 인벤토리 관리 UI(자산 관리자 전용). 등록·수정·삭제 + 사용자 배정/회수/폐기.
 * 모든 변경은 감사로 남긴다.
 */
@Controller
public class InventoryController {
  private final InventoryItemService inventoryService;
  private final InventoryImportService importService;
  private final ManagedUserService userService;
  private final com.moara.moa.group.AccessGroupService groupService;
  private final CategoryService categoryService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public InventoryController(
      InventoryItemService inventoryService, InventoryImportService importService,
      ManagedUserService userService, com.moara.moa.group.AccessGroupService groupService,
      CategoryService categoryService, AuditLogService auditLogService, TenantContext tenantContext) {
    this.inventoryService = inventoryService;
    this.importService = importService;
    this.userService = userService;
    this.groupService = groupService;
    this.categoryService = categoryService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  private static UUID parseUuid(String value) {
    return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
  }

  @GetMapping("/inventory")
  public String list(Model model) {
    if (!model.containsAttribute("inventoryForm")) {
      model.addAttribute("inventoryForm", new InventoryItemForm(null, null, null, null, null, null));
    }
    populate(model);
    return "inventory/list";
  }

  @PostMapping("/inventory")
  public String create(
      @Valid @ModelAttribute("inventoryForm") InventoryItemForm form, BindingResult binding,
      @RequestParam(required = false) String ownerGroupId, Model model) {
    InventoryItemType type = deriveType(form.category());
    if (type == null) {
      binding.rejectValue("category", "category.required", "카테고리를 선택하세요(실물/SW 아래 경로).");
    }
    if (binding.hasErrors()) {
      populate(model);
      return "inventory/list";
    }
    InventoryItemForm form2 = withType(form, type);
    try {
      InventoryItem created = inventoryService.create(tenantContext.currentTenantId(), form2);
      UUID owner = parseUuid(ownerGroupId);
      if (owner != null) {
        inventoryService.assignOwnerGroup(tenantContext.currentTenantId(), created.getId(), owner);
      }
      audit("INVENTORY_CREATE", created.getId(), created.getName());
      return "redirect:/inventory";
    } catch (DuplicateInventoryItemException exception) {
      binding.reject("inventory.duplicate", "이미 사용 중인 항목 이름입니다.");
      populate(model);
      return "inventory/list";
    }
  }

  @GetMapping("/inventory/{id}/edit")
  public String editForm(@PathVariable UUID id, Model model) {
    InventoryItem item = inventoryService.findById(tenantContext.currentTenantId(), id);
    model.addAttribute("itemId", id);
    model.addAttribute("inventoryForm", new InventoryItemForm(
        item.getName(), item.getType(), item.getCategory(), item.getSerialNo(),
        item.getExpiresAt(), item.getPurchaseDate(), item.getWarrantyEnds(), item.getLeaseEnds(),
        item.getNote()));
    model.addAttribute("assetCategories", assetCategoryNodes());
    return "inventory/form";
  }

  @PostMapping("/inventory/{id}")
  public String update(
      @PathVariable UUID id, @Valid @ModelAttribute("inventoryForm") InventoryItemForm form,
      BindingResult binding, Model model) {
    InventoryItemType type = deriveType(form.category());
    if (type == null) {
      binding.rejectValue("category", "category.required", "카테고리를 선택하세요(실물/SW 아래 경로).");
    }
    if (binding.hasErrors()) {
      model.addAttribute("itemId", id);
      model.addAttribute("assetCategories", assetCategoryNodes());
      return "inventory/form";
    }
    try {
      inventoryService.update(tenantContext.currentTenantId(), id, withType(form, type));
      audit("INVENTORY_UPDATE", id, form.name());
      return "redirect:/inventory";
    } catch (DuplicateInventoryItemException exception) {
      binding.reject("inventory.duplicate", "이미 사용 중인 항목 이름입니다.");
      model.addAttribute("itemId", id);
      model.addAttribute("assetCategories", assetCategoryNodes());
      return "inventory/form";
    }
  }

  @PostMapping("/inventory/{id}/delete")
  public String delete(@PathVariable UUID id) {
    inventoryService.delete(tenantContext.currentTenantId(), id);
    audit("INVENTORY_DELETE", id, null);
    return "redirect:/inventory";
  }

  @PostMapping("/inventory/{id}/assign")
  public String assign(@PathVariable UUID id, @RequestParam UUID userId) {
    inventoryService.assign(tenantContext.currentTenantId(), id, userId);
    audit("INVENTORY_ASSIGN", id, "user=" + userId);
    return "redirect:/inventory";
  }

  @PostMapping("/inventory/{id}/reclaim")
  public String reclaim(@PathVariable UUID id) {
    inventoryService.reclaim(tenantContext.currentTenantId(), id);
    audit("INVENTORY_RECLAIM", id, null);
    return "redirect:/inventory";
  }

  @PostMapping("/inventory/{id}/retire")
  public String retire(@PathVariable UUID id) {
    inventoryService.retire(tenantContext.currentTenantId(), id);
    audit("INVENTORY_RETIRE", id, null);
    return "redirect:/inventory";
  }

  /** 소유팀 배정/해제(자산 관리자). 빈 값=해제(자산관리자 전용으로). */
  @PostMapping("/inventory/{id}/owner")
  public String assignOwner(@PathVariable UUID id, @RequestParam(required = false) String ownerGroupId) {
    UUID groupId = parseUuid(ownerGroupId);
    inventoryService.assignOwnerGroup(tenantContext.currentTenantId(), id, groupId);
    audit("INVENTORY_SET_OWNER", id, groupId == null ? "해제" : "group=" + groupId);
    return "redirect:/inventory";
  }

  @GetMapping("/inventory/import")
  public String importForm(Model model) {
    model.addAttribute("page", "inventory");
    model.addAttribute("pageTitle", "인벤토리 대량 등록");
    model.addAttribute("projectName", "MOA");
    return "inventory/import";
  }

  @PostMapping("/inventory/import")
  public String importCsv(
      @RequestParam(value = "csvText", required = false) String csvText,
      @RequestParam(value = "csvFile", required = false) MultipartFile csvFile, Model model) {
    String csv = csvText;
    if ((csv == null || csv.isBlank()) && csvFile != null && !csvFile.isEmpty()) {
      try {
        csv = new String(csvFile.getBytes(), StandardCharsets.UTF_8);
      } catch (IOException exception) {
        model.addAttribute("error", "파일을 읽지 못했습니다: " + exception.getMessage());
        return importForm(model);
      }
    }
    if (csv == null || csv.isBlank()) {
      model.addAttribute("error", "CSV 내용을 붙여넣거나 파일을 선택하세요.");
      return importForm(model);
    }
    InventoryImportResult result = importService.importCsv(tenantContext.currentTenantId(), csv);
    audit("INVENTORY_IMPORT", null,
        "created=" + result.created() + " failed=" + result.errors().size());
    model.addAttribute("result", result);
    model.addAttribute("page", "inventory");
    model.addAttribute("pageTitle", "인벤토리 대량 등록");
    model.addAttribute("projectName", "MOA");
    return "inventory/import";
  }

  private void populate(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    model.addAttribute("items", inventoryService.findAll(tenantId));
    model.addAttribute("assetCategories", assetCategoryNodes());
    java.util.List<ManagedUser> tenantUsers = userService.findByTenant(tenantId);
    model.addAttribute("tenantUsers", tenantUsers);
    Map<UUID, String> userNames = new HashMap<>();
    for (ManagedUser u : tenantUsers) {
      userNames.put(u.getId(), u.getName() + " (" + u.getUsername() + ")");
    }
    model.addAttribute("userNames", userNames);
    java.util.List<com.moara.moa.group.AccessGroup> groups = groupService.findAll(tenantId);
    model.addAttribute("groups", groups);
    Map<UUID, String> groupNames = new HashMap<>();
    for (com.moara.moa.group.AccessGroup g : groups) {
      groupNames.put(g.getId(), g.getName());
    }
    model.addAttribute("groupNames", groupNames);
    model.addAttribute("page", "inventory");
    model.addAttribute("pageTitle", "인벤토리");
    model.addAttribute("projectName", "MOA");
  }

  /** 등록 폼 셀렉트용 자산 카테고리 트리(경로·깊이 포함, DFS 순). */
  private java.util.List<com.moara.moa.category.CategoryService.CategoryNode> assetCategoryNodes() {
    return categoryService.assetTree(tenantContext.currentTenantId());
  }

  /** 카테고리 경로의 최상위(실물/SW)로 유형을 파생. 알 수 없으면 null. */
  private static InventoryItemType deriveType(String categoryPath) {
    if (categoryPath == null || categoryPath.isBlank()) {
      return null;
    }
    String root = categoryPath.split(java.util.regex.Pattern.quote(CategoryService.PATH_SEP))[0].trim();
    for (InventoryItemType type : InventoryItemType.values()) {
      if (type.getLabel().equals(root)) {
        return type;
      }
    }
    return null;
  }

  private static InventoryItemForm withType(InventoryItemForm form, InventoryItemType type) {
    return new InventoryItemForm(form.name(), type, form.category(), form.serialNo(),
        form.expiresAt(), form.purchaseDate(), form.warrantyEnds(), form.leaseEnds(), form.note());
  }

  private void audit(String action, UUID targetId, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "InventoryItem", targetId,
          AuditResult.SUCCESS, message);
    }
  }
}
