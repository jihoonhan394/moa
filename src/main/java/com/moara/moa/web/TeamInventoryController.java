package com.moara.moa.web;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.inventory.DuplicateInventoryItemException;
import com.moara.moa.inventory.InventoryItem;
import com.moara.moa.inventory.InventoryItemForm;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.inventory.InventoryItemType;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * 부서장 위임 — <b>본인이 이끄는 그룹이 소유한 인벤토리</b>의 추가·편집·삭제·배정을 부서장이 직접 한다.
 * 관리자 라우트(/inventory=자산관리자 전용)를 열지 않고 팀 콘솔 아래 별도 스코프 화면으로 두어,
 * 모든 작업 대상을 <b>리더가 이끄는 그룹 소유</b>로 엄격히 제한한다(자산관리자는 /inventory에서 전체 관리).
 */
@Controller
public class TeamInventoryController {
  private final InventoryItemService inventoryService;
  private final AccessGroupService groupService;
  private final ManagedUserService userService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public TeamInventoryController(
      InventoryItemService inventoryService, AccessGroupService groupService,
      ManagedUserService userService, AuditLogService auditLogService, TenantContext tenantContext) {
    this.inventoryService = inventoryService;
    this.groupService = groupService;
    this.userService = userService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/team/inventory")
  public String index(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID userId = tenantContext.currentUserId();
    List<AccessGroup> ledGroups = groupService.groupsLedBy(tenantId, userId);
    Set<UUID> ledGroupIds = ledGroups.stream().map(AccessGroup::getId).collect(Collectors.toSet());

    List<InventoryItem> items = inventoryService.findOwnedByGroups(tenantId, ledGroupIds);
    Map<UUID, String> groupNames = new HashMap<>();
    ledGroups.forEach(g -> groupNames.put(g.getId(), g.getName()));

    // 배정 대상: 내가 이끄는 팀의 팀원.
    Set<UUID> teamIds = groupService.teamMemberIds(tenantId, userId);
    List<ManagedUser> teamUsers = userService.findByTenant(tenantId).stream()
        .filter(u -> teamIds.contains(u.getId())).toList();
    Map<UUID, String> userNames = new HashMap<>();
    for (ManagedUser u : userService.findByTenant(tenantId)) {
      userNames.put(u.getId(), u.getName() + " (" + u.getUsername() + ")");
    }

    if (!model.containsAttribute("inventoryForm")) {
      model.addAttribute("inventoryForm", new InventoryItemForm(null, null, null, null, null, null));
    }
    model.addAttribute("items", items);
    model.addAttribute("ledGroups", ledGroups);
    model.addAttribute("groupNames", groupNames);
    model.addAttribute("teamUsers", teamUsers);
    model.addAttribute("userNames", userNames);
    model.addAttribute("types", InventoryItemType.values());
    model.addAttribute("page", "team");
    model.addAttribute("pageTitle", "우리 팀 자산 관리");
    model.addAttribute("projectName", "MOA");
    return "team/inventory";
  }

  @PostMapping("/team/inventory")
  public String create(
      @Valid @ModelAttribute("inventoryForm") InventoryItemForm form, BindingResult binding,
      @RequestParam UUID ownerGroupId, RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!leads(tenantId, ownerGroupId)) {
      redirect.addFlashAttribute("teamError", "본인이 이끄는 부서로만 자산을 등록할 수 있습니다.");
      return "redirect:/team/inventory";
    }
    if (binding.hasErrors()) {
      redirect.addFlashAttribute("teamError", "입력값을 확인하세요(이름·유형 필수).");
      return "redirect:/team/inventory";
    }
    try {
      InventoryItem created = inventoryService.create(tenantId, form);
      inventoryService.assignOwnerGroup(tenantId, created.getId(), ownerGroupId);
      audit("TEAM_INVENTORY_CREATE", created.getId(), created.getName());
      redirect.addFlashAttribute("teamOk", "자산을 등록했습니다: " + created.getName());
    } catch (DuplicateInventoryItemException e) {
      redirect.addFlashAttribute("teamError", "이미 사용 중인 항목 이름입니다.");
    }
    return "redirect:/team/inventory";
  }

  @PostMapping("/team/inventory/{id}")
  public String update(
      @PathVariable UUID id, @Valid @ModelAttribute("inventoryForm") InventoryItemForm form,
      BindingResult binding, RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!canManage(tenantId, id)) {
      redirect.addFlashAttribute("teamError", "본인 부서가 소유한 자산만 수정할 수 있습니다.");
      return "redirect:/team/inventory";
    }
    if (binding.hasErrors()) {
      redirect.addFlashAttribute("teamError", "입력값을 확인하세요.");
      return "redirect:/team/inventory";
    }
    try {
      inventoryService.update(tenantId, id, form);
      audit("TEAM_INVENTORY_UPDATE", id, form.name());
      redirect.addFlashAttribute("teamOk", "수정했습니다: " + form.name());
    } catch (DuplicateInventoryItemException e) {
      redirect.addFlashAttribute("teamError", "이미 사용 중인 항목 이름입니다.");
    }
    return "redirect:/team/inventory";
  }

  @PostMapping("/team/inventory/{id}/delete")
  public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!canManage(tenantId, id)) {
      redirect.addFlashAttribute("teamError", "본인 부서가 소유한 자산만 삭제할 수 있습니다.");
      return "redirect:/team/inventory";
    }
    inventoryService.delete(tenantId, id);
    audit("TEAM_INVENTORY_DELETE", id, null);
    redirect.addFlashAttribute("teamOk", "삭제했습니다.");
    return "redirect:/team/inventory";
  }

  @PostMapping("/team/inventory/{id}/assign")
  public String assign(
      @PathVariable UUID id, @RequestParam UUID userId, RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!canManage(tenantId, id)) {
      redirect.addFlashAttribute("teamError", "본인 부서가 소유한 자산만 배정할 수 있습니다.");
      return "redirect:/team/inventory";
    }
    if (!groupService.leads(tenantId, tenantContext.currentUserId(), userId)) {
      redirect.addFlashAttribute("teamError", "본인 팀원에게만 배정할 수 있습니다.");
      return "redirect:/team/inventory";
    }
    inventoryService.assign(tenantId, id, userId);
    audit("TEAM_INVENTORY_ASSIGN", id, "user=" + userId);
    redirect.addFlashAttribute("teamOk", "배정했습니다.");
    return "redirect:/team/inventory";
  }

  @PostMapping("/team/inventory/{id}/reclaim")
  public String reclaim(@PathVariable UUID id, RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!canManage(tenantId, id)) {
      redirect.addFlashAttribute("teamError", "본인 부서가 소유한 자산만 회수할 수 있습니다.");
      return "redirect:/team/inventory";
    }
    inventoryService.reclaim(tenantId, id);
    audit("TEAM_INVENTORY_RECLAIM", id, null);
    redirect.addFlashAttribute("teamOk", "회수했습니다.");
    return "redirect:/team/inventory";
  }

  /** 대상 자산이 '내가 이끄는 그룹' 소유인지. 소유팀 없는 자산·타팀 소유는 불가(정보 비노출 겸 방어선). */
  private boolean canManage(UUID tenantId, UUID itemId) {
    UUID owner;
    try {
      owner = inventoryService.findById(tenantId, itemId).getOwnerGroupId();
    } catch (RuntimeException notFound) {
      return false;
    }
    return owner != null && leads(tenantId, owner);
  }

  private boolean leads(UUID tenantId, UUID groupId) {
    return groupService.groupsLedBy(tenantId, tenantContext.currentUserId()).stream()
        .anyMatch(g -> g.getId().equals(groupId));
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
