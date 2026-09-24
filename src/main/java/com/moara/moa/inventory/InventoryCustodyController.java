package com.moara.moa.inventory;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 자산 보관 장부 화면. "이 장비가 지금까지 누구 손을 거쳤나"에 답한다.
 *
 * <p>경로가 {@code /inventory/**} 아래라 기존 자산 관리자 인가를 그대로 물려받는다
 * (트래커 패널이 쓰는 방식과 같다).
 */
@Controller
public class InventoryCustodyController {
  private final InventoryItemService inventoryService;
  private final InventoryCustodyService custodyService;
  private final ManagedUserService userService;
  private final AccessGroupService groupService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public InventoryCustodyController(
      InventoryItemService inventoryService, InventoryCustodyService custodyService,
      ManagedUserService userService, AccessGroupService groupService,
      AuditLogService auditLogService, TenantContext tenantContext) {
    this.inventoryService = inventoryService;
    this.custodyService = custodyService;
    this.userService = userService;
    this.groupService = groupService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/inventory/{id}/custody")
  public String custody(@PathVariable UUID id, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    InventoryItem item = inventoryService.findById(tenantId, id); // 소유권 검증
    List<InventoryCustody> history = custodyService.history(tenantId, id);
    model.addAttribute("item", item);
    model.addAttribute("rows", rows(tenantId, history));
    model.addAttribute("current", custodyService.current(tenantId, id).orElse(null));
    model.addAttribute("tenantUsers", userService.findByTenant(tenantId));
    model.addAttribute("groups", groupService.findAll(tenantId));
    model.addAttribute("today", LocalDate.now());
    model.addAttribute("page", "inventory");
    return "inventory/custody";
  }

  /**
   * 보관 이동 기록. 사내 이동(창고·사용자·부서)만 받는다 — 고객처 납품은 2b, 부품 장착은 2c다.
   * 화면 밖 경로로 그 값들이 들어와도 서비스가 아니라 <b>여기서</b> 막는다. 아직 화면·규칙이
   * 준비되지 않은 상태로 장부에 들어가면 이력이 오염되기 때문이다.
   */
  @PostMapping("/inventory/{id}/custody")
  public String move(
      @PathVariable UUID id, @RequestParam InventoryHolderType holderType,
      @RequestParam(required = false) UUID holderId,
      @RequestParam(required = false) String reason,
      @RequestParam(required = false) String note,
      RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    inventoryService.findById(tenantId, id); // 소유권 검증
    if (!isInternalMove(holderType)) {
      redirect.addFlashAttribute("custodyError", "아직 사내 이동(창고·사용자·부서)만 기록할 수 있습니다.");
      return "redirect:/inventory/" + id + "/custody";
    }
    if (holderType.isInternalTarget() && holderId == null) {
      redirect.addFlashAttribute("custodyError", "대상(사용자 또는 부서)을 선택하세요.");
      return "redirect:/inventory/" + id + "/custody";
    }
    // 사용자 배정은 인벤토리 상태(assignedUserId)와 함께 움직여야 하므로 품목 서비스를 거친다.
    // 그래야 목록·퇴사 회수·팀 경계가 보는 캐시와 장부가 어긋나지 않는다.
    if (holderType == InventoryHolderType.USER) {
      inventoryService.assign(tenantId, id, holderId, tenantContext.currentUserId());
    } else if (holderType == InventoryHolderType.WAREHOUSE) {
      inventoryService.reclaim(tenantId, id, tenantContext.currentUserId(),
          reason == null || reason.isBlank() ? "창고 입고" : reason);
    } else {
      custodyService.transfer(tenantId, id, holderType, holderId, null, null,
          reason, note, tenantContext.currentUserId());
    }
    audit(id, holderType, holderId);
    redirect.addFlashAttribute("custodyMessage", "보관 이동을 기록했습니다.");
    return "redirect:/inventory/" + id + "/custody";
  }

  private boolean isInternalMove(InventoryHolderType type) {
    return type == InventoryHolderType.WAREHOUSE
        || type == InventoryHolderType.USER
        || type == InventoryHolderType.GROUP;
  }

  /** 표시용 행: 보관자 ID를 이름으로 풀고, 반납 초과 여부를 미리 계산한다. */
  private List<Map<String, Object>> rows(UUID tenantId, List<InventoryCustody> history) {
    Map<UUID, String> userNames = userService.namesByTenant(tenantId);
    Map<UUID, String> groupNames = new HashMap<>();
    for (AccessGroup group : groupService.findAll(tenantId)) {
      groupNames.put(group.getId(), group.getName());
    }
    Map<UUID, String> actorNames = userNames;
    LocalDate today = LocalDate.now();
    List<Map<String, Object>> rows = new ArrayList<>();
    for (InventoryCustody custody : history) {
      Map<String, Object> row = new HashMap<>();
      row.put("holderLabel", holderLabel(custody, userNames, groupNames));
      row.put("holderType", custody.getHolderType().getLabel());
      row.put("startedOn", custody.getStartedOn());
      row.put("endedOn", custody.getEndedOn());
      row.put("expectedReturnOn", custody.getExpectedReturnOn());
      row.put("overdue", custody.isReturnOverdue(today));
      row.put("reason", custody.getReason());
      row.put("note", custody.getNote());
      row.put("quantity", custody.getQuantity());
      row.put("actor", custody.getCreatedBy() == null
          ? "시스템" : actorNames.getOrDefault(custody.getCreatedBy(), "알 수 없음"));
      rows.add(row);
    }
    return rows;
  }

  private String holderLabel(
      InventoryCustody custody, Map<UUID, String> userNames, Map<UUID, String> groupNames) {
    return switch (custody.getHolderType()) {
      case USER -> userNames.getOrDefault(custody.getHolderId(), "(삭제된 사용자)");
      case GROUP -> groupNames.getOrDefault(custody.getHolderId(), "(삭제된 부서)");
      case PARENT_ITEM -> "장비 내부";
      case WAREHOUSE -> "창고";
      case DISPOSED -> "폐기";
      case CUSTOMER, VENDOR -> custody.getHolderName() == null ? "(이름 없음)" : custody.getHolderName();
    };
  }

  private void audit(UUID itemId, InventoryHolderType holderType, UUID holderId) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, "INVENTORY_CUSTODY_MOVE", "InventoryItem",
          itemId, AuditResult.SUCCESS,
          "보관 이동 " + holderType + (holderId == null ? "" : ":" + holderId));
    }
  }
}
