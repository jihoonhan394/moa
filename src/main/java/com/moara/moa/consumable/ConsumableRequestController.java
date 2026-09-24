package com.moara.moa.consumable;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUserService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 소모품 요청 처리(자산 관리자). 접수·거절·보류를 하고, 주문 등록이 완료를 만든다. */
@Controller
public class ConsumableRequestController {
  private final ConsumableRequestService requestService;
  private final ConsumableService consumableService;
  private final ManagedUserService userService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public ConsumableRequestController(
      ConsumableRequestService requestService, ConsumableService consumableService,
      ManagedUserService userService, AuditLogService auditLogService,
      TenantContext tenantContext) {
    this.requestService = requestService;
    this.consumableService = consumableService;
    this.userService = userService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/consumables/requests")
  public String list(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    Map<UUID, String> itemNames = new HashMap<>();
    for (ConsumableItem item : consumableService.findAll(tenantId)) {
      itemNames.put(item.getId(), item.getName());
    }
    Map<UUID, String> userNames = userService.namesByTenant(tenantId);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (ConsumableRequest request : requestService.findAll(tenantId)) {
      Map<String, Object> row = new HashMap<>();
      row.put("id", request.getId());
      row.put("itemId", request.getItemId());
      row.put("itemName", itemNames.getOrDefault(request.getItemId(), "(삭제된 품목)"));
      row.put("requester", userNames.getOrDefault(request.getRequestedBy(), "알 수 없음"));
      row.put("note", request.getNote());
      row.put("status", request.getStatus());
      row.put("statusLabel", request.getStatus().getLabel());
      row.put("open", request.getStatus().isOpen());
      row.put("reason", request.getDecisionReason());
      row.put("reviewOn", request.getReviewOn());
      row.put("createdAt", request.getCreatedAt());
      rows.add(row);
    }
    model.addAttribute("requests", rows);
    model.addAttribute("openCount", requestService.findOpen(tenantId).size());
    model.addAttribute("page", "consumables");
    return "consumables/requests";
  }

  @PostMapping("/consumables/requests/{id}/acknowledge")
  public String acknowledge(@PathVariable UUID id, RedirectAttributes redirect) {
    requestService.acknowledge(tenantContext.currentTenantId(), id, tenantContext.currentUserId());
    audit("CONSUMABLE_REQUEST_ACK", id, null);
    redirect.addFlashAttribute("requestMessage", "접수했습니다. 주문을 등록하면 완료됩니다.");
    return "redirect:/consumables/requests";
  }

  @PostMapping("/consumables/requests/{id}/reject")
  public String reject(
      @PathVariable UUID id, @RequestParam(required = false) String reason,
      RedirectAttributes redirect) {
    return decide(id, reason, null, false, redirect);
  }

  @PostMapping("/consumables/requests/{id}/hold")
  public String hold(
      @PathVariable UUID id, @RequestParam(required = false) String reason,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate reviewOn, RedirectAttributes redirect) {
    return decide(id, reason, reviewOn, true, redirect);
  }

  private String decide(
      UUID id, String reason, LocalDate reviewOn, boolean hold, RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    try {
      if (hold) {
        requestService.hold(tenantId, id, tenantContext.currentUserId(), reason, reviewOn);
        audit("CONSUMABLE_REQUEST_HOLD", id, reason);
        redirect.addFlashAttribute("requestMessage", "보류했습니다. 요청자에게 사유가 전달됩니다.");
      } else {
        requestService.reject(tenantId, id, tenantContext.currentUserId(), reason);
        audit("CONSUMABLE_REQUEST_REJECT", id, reason);
        redirect.addFlashAttribute("requestMessage", "거절했습니다. 요청자에게 사유가 전달됩니다.");
      }
    } catch (IllegalArgumentException rejected) {
      redirect.addFlashAttribute("requestError", rejected.getMessage());
    }
    return "redirect:/consumables/requests";
  }

  private void audit(String action, UUID targetId, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "ConsumableRequest", targetId,
          AuditResult.SUCCESS, message);
    }
  }
}
