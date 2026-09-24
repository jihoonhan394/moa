package com.moara.moa.inventory;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUserService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 내게 배정된 자산의 상세·보관 이력(읽기 전용).
 *
 * <p>장부를 만들어 놓고 정작 <b>당사자가 볼 수 없으면</b> 반쪽이다. 퇴사·인수인계 때
 * "이거 제가 언제 받은 게 맞나요?"에 본인이 답할 수 있어야 한다. 관리 화면
 * ({@code /inventory/**})은 자산 관리자 전용이라 일반 사용자는 들어갈 수 없다.
 *
 * <p>경로가 {@code /my/**}라 인증만 있으면 열리므로, <b>무엇을 볼 수 있는지는 이 컨트롤러가
 * 직접 판정한다</b> — 내게 배정됐거나, 내가 속한 팀이 소유한 자산만이다.
 * 화면에서만 막지 않고 서비스 호출 전에 막는다.
 */
@Controller
public class MyInventoryController {
  private final InventoryItemService inventoryService;
  private final InventoryCustodyService custodyService;
  private final InventoryPartService partService;
  private final AccessGroupService groupService;
  private final ManagedUserService userService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public MyInventoryController(
      InventoryItemService inventoryService, InventoryCustodyService custodyService,
      InventoryPartService partService, AccessGroupService groupService,
      ManagedUserService userService, AuditLogService auditLogService,
      TenantContext tenantContext) {
    this.auditLogService = auditLogService;
    this.inventoryService = inventoryService;
    this.custodyService = custodyService;
    this.partService = partService;
    this.groupService = groupService;
    this.userService = userService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/my/assets/{id}")
  public String detail(@PathVariable UUID id, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID userId = tenantContext.currentUserId();
    InventoryItem item = inventoryService.findById(tenantId, id);
    requireMine(tenantId, userId, item);

    model.addAttribute("item", item);
    model.addAttribute("rows", historyRows(tenantId, id));
    model.addAttribute("parts", partService.partsOf(tenantId, id));
    model.addAttribute("mine", userId.equals(item.getAssignedUserId()));
    InventoryCustody active = custodyService.current(tenantId, id).orElse(null);
    model.addAttribute("awaitingConfirm",
        active != null && active.awaitsConfirmation() && userId.equals(active.getHolderId()));
    model.addAttribute("confirmedAt", active == null ? null : active.getConfirmedAt());
    model.addAttribute("page", "my-workspace");
    return "my/asset";
  }

  /**
   * 인수 확인. 본인만 누를 수 있다는 판정은 서비스가 하고, 여기서는 결과만 안내한다.
   * 확인은 <b>되돌리지 않는다</b> — 취소가 되면 "받았다"는 기록의 무게가 사라진다.
   * 잘못 눌렀다면 자산 담당자가 배정을 되돌리는 것이 맞는 경로다.
   */
  @PostMapping("/my/assets/{id}/confirm")
  public String confirm(@PathVariable UUID id, RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID userId = tenantContext.currentUserId();
    InventoryItem item = inventoryService.findById(tenantId, id);
    requireMine(tenantId, userId, item);
    if (custodyService.confirmReceipt(tenantId, id, userId)) {
      auditLogService.recordTenantAction(tenantId, userId, "INVENTORY_RECEIPT_CONFIRM",
          "InventoryItem", id, AuditResult.SUCCESS, "인수 확인 — " + item.getName());
      redirect.addFlashAttribute("assetMessage", "받으신 것으로 기록했습니다.");
    } else {
      redirect.addFlashAttribute("assetMessage", "이미 확인했거나 확인 대상이 아닙니다.");
    }
    return "redirect:/my/assets/" + id;
  }

  /**
   * 내게 배정됐거나 내 팀이 소유한 자산만. 아니면 <b>존재 자체를 알리지 않는다</b>(404) —
   * 403은 "그런 자산이 있긴 하다"를 알려 주는 셈이라, 남의 자산 ID를 넣어 보며 대장을
   * 훑을 수 있게 된다.
   */
  private void requireMine(UUID tenantId, UUID userId, InventoryItem item) {
    if (userId == null) {
      throw new AccessDeniedException("로그인이 필요합니다.");
    }
    if (userId.equals(item.getAssignedUserId())) {
      return;
    }
    if (item.getOwnerGroupId() != null && myGroupIds(tenantId, userId).contains(item.getOwnerGroupId())) {
      return;
    }
    throw new InventoryItemNotFoundException(item.getId());
  }

  private List<UUID> myGroupIds(UUID tenantId, UUID userId) {
    return groupService.findGroupsOfUser(tenantId, userId).stream()
        .map(com.moara.moa.group.UserGroupMember::getGroupId).toList();
  }

  /**
   * 표시용 보관 이력. 관리 화면과 달리 <b>기록자는 보여 주지 않는다</b> — 사용자가 알아야 할
   * 것은 "언제부터 누구에게 있었나"이고, 누가 입력했는지는 관리·감사의 몫이다.
   */
  private List<Map<String, Object>> historyRows(UUID tenantId, UUID itemId) {
    Map<UUID, String> userNames = userService.namesByTenant(tenantId);
    Map<UUID, String> groupNames = new HashMap<>();
    for (AccessGroup group : groupService.findAll(tenantId)) {
      groupNames.put(group.getId(), group.getName());
    }
    LocalDate today = LocalDate.now();
    List<Map<String, Object>> rows = new ArrayList<>();
    for (InventoryCustody custody : custodyService.history(tenantId, itemId)) {
      Map<String, Object> row = new HashMap<>();
      row.put("holderLabel", switch (custody.getHolderType()) {
        case USER -> userNames.getOrDefault(custody.getHolderId(), "(퇴사자)");
        case GROUP -> groupNames.getOrDefault(custody.getHolderId(), "(없어진 부서)");
        case PARENT_ITEM -> "다른 장비에 장착";
        case WAREHOUSE -> "회사 보관";
        case DISPOSED -> "폐기";
        case CUSTOMER, VENDOR -> custody.getHolderName() == null
            ? custody.getHolderType().getLabel() : custody.getHolderName();
      });
      row.put("startedOn", custody.getStartedOn());
      row.put("endedOn", custody.getEndedOn());
      row.put("reason", custody.getReason());
      row.put("overdue", custody.isReturnOverdue(today));
      rows.add(row);
    }
    return rows;
  }
}
