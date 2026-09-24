package com.moara.moa.consumable;

import com.moara.moa.notification.NotificationService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
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
 * 일반 사용자의 소모품 요청. <b>전 직원이 정기적으로 쓰는 첫 쓰기 동선</b>이다 —
 * 지금까지 일반 사용자에게 MOA는 대부분 읽기 전용이었다.
 *
 * <p>경로가 {@code /my/**}라 인증만 있으면 열린다. 관리 화면({@code /consumables/**})은
 * 자산 관리자 전용이므로 여기서는 <b>요청만</b> 할 수 있고 품목을 만들거나 고칠 수 없다.
 */
@Controller
public class MyConsumableController {
  private final ConsumableService consumableService;
  private final ConsumableRequestService requestService;
  private final ManagedUserService userService;
  private final NotificationService notificationService;
  private final TenantContext tenantContext;

  public MyConsumableController(
      ConsumableService consumableService, ConsumableRequestService requestService,
      ManagedUserService userService, NotificationService notificationService,
      TenantContext tenantContext) {
    this.consumableService = consumableService;
    this.requestService = requestService;
    this.userService = userService;
    this.notificationService = notificationService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/my/consumables")
  public String mine(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID userId = tenantContext.currentUserId();
    Map<UUID, String> itemNames = new HashMap<>();
    for (ConsumableItem item : consumableService.findAll(tenantId)) {
      itemNames.put(item.getId(), item.getName());
    }
    List<Map<String, Object>> rows = new ArrayList<>();
    for (ConsumableRequest request : requestService.findMine(tenantId, userId)) {
      Map<String, Object> row = new HashMap<>();
      row.put("id", request.getId());
      row.put("itemName", itemNames.getOrDefault(request.getItemId(), "(삭제된 품목)"));
      row.put("note", request.getNote());
      row.put("status", request.getStatus().getLabel());
      row.put("reason", request.getDecisionReason());
      row.put("createdAt", request.getCreatedAt());
      row.put("cancelable", request.cancelableBy(userId));
      rows.add(row);
    }
    model.addAttribute("items", consumableService.findActive(tenantId));
    model.addAttribute("requests", rows);
    model.addAttribute("openCounts", requestService.openCountByItem(tenantId));
    model.addAttribute("page", "my-consumables");
    return "my/consumables";
  }

  @PostMapping("/my/consumables")
  public String request(
      @RequestParam UUID itemId, @RequestParam(required = false) String note,
      RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    try {
      boolean first = requestService.request(tenantId, itemId, tenantContext.currentUserId(), note);
      if (first) {
        notifyManagers(tenantId, itemId);
      }
      redirect.addFlashAttribute("requestMessage",
          "요청을 접수했습니다. 처리되면 알림으로 알려 드립니다.");
    } catch (IllegalArgumentException rejected) {
      redirect.addFlashAttribute("requestError", rejected.getMessage());
    }
    return "redirect:/my/consumables";
  }

  @PostMapping("/my/consumables/{id}/cancel")
  public String cancel(@PathVariable UUID id, RedirectAttributes redirect) {
    requestService.cancel(tenantContext.currentTenantId(), id, tenantContext.currentUserId());
    redirect.addFlashAttribute("requestMessage", "요청을 취소했습니다.");
    return "redirect:/my/consumables";
  }

  /**
   * 담당자에게 알린다. 같은 품목의 <b>첫 요청에만</b> 부른다 — 다섯 명이 각각 요청할 때마다
   * 알리면 담당자가 알림을 꺼 버린다.
   */
  private void notifyManagers(UUID tenantId, UUID itemId) {
    ConsumableItem item = consumableService.findById(tenantId, itemId);
    for (ManagedUser manager : userService.findByTenant(tenantId)) {
      if (manager.getStatus() != UserStatus.ACTIVE) {
        continue;
      }
      if (!manager.hasRole(UserRole.ASSET_MANAGER) && !manager.hasRole(UserRole.TENANT_ADMIN)) {
        continue;
      }
      notificationService.notify(tenantId, manager.getId(),
          "[소모품 요청] " + item.getName(),
          "직원이 소모품을 요청했습니다. 요청 목록에서 확인해 주세요.",
          "/consumables/requests");
    }
  }
}
