package com.moara.moa.consumable;

import com.moara.moa.audit.TenantAuditRecorder;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.support.Values;
import com.moara.moa.user.ManagedUserService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 소모품 관리(자산 관리자). 품목 등록과 <b>주문 기록</b>이 전부다 — 재고 수량은 다루지 않는다.
 *
 * <p>주문 등록을 얼마나 덜 귀찮게 하느냐가 이 기능의 성패를 가른다. 계산식이 아무리 좋아도
 * 주문이 기록되지 않으면 예측할 재료가 없다. 그래서 목록에서 버튼 한 번으로 끝나게 하고,
 * 날짜는 오늘·수량은 직전 주문량을 기본값으로 채운다.
 */
@Controller
public class ConsumableController {
  private final ConsumableService consumableService;
  private final ConsumableRequestService requestService;
  private final AccessGroupService groupService;
  private final ManagedUserService userService;
  private final TenantAuditRecorder auditRecorder;
  private final TenantContext tenantContext;

  public ConsumableController(
      ConsumableService consumableService, ConsumableRequestService requestService,
      AccessGroupService groupService,
      ManagedUserService userService, TenantAuditRecorder auditRecorder,
      TenantContext tenantContext) {
    this.consumableService = consumableService;
    this.requestService = requestService;
    this.groupService = groupService;
    this.userService = userService;
    this.auditRecorder = auditRecorder;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/consumables")
  public String list(Model model) {
    if (!model.containsAttribute("itemForm")) {
      model.addAttribute("itemForm", new ConsumableItemForm(null, null, null, null, null));
    }
    populate(model);
    return "consumables/list";
  }

  @PostMapping("/consumables")
  public String create(
      @Valid @ModelAttribute("itemForm") ConsumableItemForm itemForm, BindingResult binding,
      @RequestParam(required = false) String ownerGroupId, Model model) {
    if (binding.hasErrors()) {
      populate(model);
      return "consumables/list";
    }
    UUID tenantId = tenantContext.currentTenantId();
    try {
      ConsumableItem created = consumableService.create(tenantId, itemForm);
      UUID owner = parseUuid(ownerGroupId);
      if (owner != null) {
        consumableService.assignOwnerGroup(tenantId, created.getId(), owner);
      }
      audit("CONSUMABLE_CREATE", created.getId(), created.getName());
      return "redirect:/consumables";
    } catch (DuplicateConsumableItemException duplicate) {
      binding.reject("consumable.duplicate", "이미 등록된 소모품 이름입니다.");
      populate(model);
      return "consumables/list";
    }
  }

  @GetMapping("/consumables/{id}")
  public String detail(@PathVariable UUID id, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    ConsumableItem item = consumableService.findById(tenantId, id);
    LocalDate today = LocalDate.now();
    List<ConsumableOrder> orders = consumableService.orders(tenantId, id);
    ConsumableForecast forecast = ConsumableForecast.of(orders, item.getCycleDays(), today);
    model.addAttribute("item", item);
    model.addAttribute("orders", orderRows(tenantId, orders));
    model.addAttribute("forecast", forecast);
    model.addAttribute("today", today);
    model.addAttribute("suggestsChange", forecast.suggestsCycleChange(item.getCycleDays()));
    if (!model.containsAttribute("orderForm")) {
      // 날짜는 오늘, 수량은 직전 주문량 — 손댈 것이 없으면 실제로 기록한다.
      model.addAttribute("orderForm", new ConsumableOrderForm(
          today, orders.isEmpty() ? 1 : orders.get(0).getQuantity(), null));
    }
    model.addAttribute("itemForm", new ConsumableItemForm(
        item.getName(), item.getCategory(), item.getUnit(), item.getCycleDays(), item.getNote()));
    model.addAttribute("groups", groupService.findAll(tenantId));
    model.addAttribute("page", "consumables");
    return "consumables/detail";
  }

  @PostMapping("/consumables/{id}")
  public String update(
      @PathVariable UUID id, @Valid @ModelAttribute("itemForm") ConsumableItemForm itemForm,
      BindingResult binding, @RequestParam(required = false) String ownerGroupId,
      RedirectAttributes redirect) {
    if (binding.hasErrors()) {
      redirect.addFlashAttribute("consumableError", "입력값을 확인하세요.");
      return "redirect:/consumables/" + id;
    }
    UUID tenantId = tenantContext.currentTenantId();
    try {
      consumableService.update(tenantId, id, itemForm);
      consumableService.assignOwnerGroup(tenantId, id, parseUuid(ownerGroupId));
      audit("CONSUMABLE_UPDATE", id, itemForm.name());
      redirect.addFlashAttribute("consumableMessage", "수정했습니다.");
    } catch (DuplicateConsumableItemException duplicate) {
      redirect.addFlashAttribute("consumableError", "이미 등록된 소모품 이름입니다.");
    }
    return "redirect:/consumables/" + id;
  }

  /** 주문 기록. 이 기능 전체가 여기에 달려 있다 — 기록되지 않으면 예측할 재료가 없다. */
  @PostMapping("/consumables/{id}/orders")
  public String addOrder(
      @PathVariable UUID id, @Valid @ModelAttribute("orderForm") ConsumableOrderForm orderForm,
      BindingResult binding, RedirectAttributes redirect) {
    if (binding.hasErrors()) {
      redirect.addFlashAttribute("consumableError", "주문일과 수량을 확인하세요.");
      return "redirect:/consumables/" + id;
    }
    try {
      ConsumableOrder order = consumableService.addOrder(
          tenantContext.currentTenantId(), id, orderForm, tenantContext.currentUserId());
      audit("CONSUMABLE_ORDER_ADD", id,
          order.getOrderedOn() + " · " + order.getQuantity());
      // 주문 한 번으로 요청 처리까지 끝난다 — 담당자가 따로 누를 것이 없고,
      // 요청자에게는 "주문했습니다" 알림이 간다.
      int closed = requestService.fulfillByOrder(
          tenantContext.currentTenantId(), id, order.getId(), tenantContext.currentUserId());
      redirect.addFlashAttribute("consumableMessage", closed > 0
          ? "주문을 기록하고 요청 " + closed + "건을 완료 처리했습니다."
          : "주문을 기록했습니다.");
    } catch (IllegalArgumentException rejected) {
      redirect.addFlashAttribute("consumableError", rejected.getMessage());
    }
    return "redirect:/consumables/" + id;
  }

  /** 실측 주기를 설정값으로 받아들인다. <b>담당자가 눌러야</b> 바뀐다 — 몰래 고치지 않는다. */
  @PostMapping("/consumables/{id}/adopt-cycle")
  public String adoptCycle(
      @PathVariable UUID id, @RequestParam int cycleDays, RedirectAttributes redirect) {
    consumableService.adoptCycle(tenantContext.currentTenantId(), id, cycleDays);
    audit("CONSUMABLE_CYCLE_ADOPT", id, cycleDays + "일");
    redirect.addFlashAttribute("consumableMessage", "예상 주기를 " + cycleDays + "일로 바꿨습니다.");
    return "redirect:/consumables/" + id;
  }

  @PostMapping("/consumables/{id}/active")
  public String setActive(
      @PathVariable UUID id, @RequestParam boolean active, RedirectAttributes redirect) {
    consumableService.setActive(tenantContext.currentTenantId(), id, active);
    audit(active ? "CONSUMABLE_ACTIVATE" : "CONSUMABLE_DEACTIVATE", id, null);
    redirect.addFlashAttribute("consumableMessage",
        active ? "다시 사용합니다." : "사용 중지했습니다(이력은 남습니다).");
    return "redirect:/consumables/" + id;
  }

  private void populate(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    LocalDate today = LocalDate.now();
    List<ConsumableItem> items = consumableService.findAll(tenantId);
    Map<UUID, ConsumableForecast> forecasts = consumableService.forecasts(tenantId, today);
    Map<UUID, String> groupNames = new HashMap<>();
    for (AccessGroup group : groupService.findAll(tenantId)) {
      groupNames.put(group.getId(), group.getName());
    }
    model.addAttribute("items", items);
    model.addAttribute("forecasts", forecasts);
    model.addAttribute("groupNames", groupNames);
    model.addAttribute("groups", groupService.findAll(tenantId));
    model.addAttribute("today", today);
    model.addAttribute("openRequests", requestService.openCountByItem(tenantId));
    model.addAttribute("dueCount", items.stream()
        .filter(i -> i.isActive() && forecasts.get(i.getId()).isDue(today)).count());
    model.addAttribute("page", "consumables");
  }

  private List<Map<String, Object>> orderRows(UUID tenantId, List<ConsumableOrder> orders) {
    Map<UUID, String> names = userService.namesByTenant(tenantId);
    List<Map<String, Object>> rows = new ArrayList<>();
    for (ConsumableOrder order : orders) {
      Map<String, Object> row = new HashMap<>();
      row.put("orderedOn", order.getOrderedOn());
      row.put("quantity", order.getQuantity());
      row.put("note", order.getNote());
      row.put("actor", order.getCreatedBy() == null
          ? "-" : names.getOrDefault(order.getCreatedBy(), "알 수 없음"));
      rows.add(row);
    }
    return rows;
  }

  /** 선택 항목으로 넘어온 식별자. 안 고르거나 망가진 값은 안 고른 것으로 본다. */
  private UUID parseUuid(String raw) {
    return Values.optionalUuid(raw);
  }

  /** 특권 행위 기록. 정책(행위자 없으면 미기록 등)은 TenantAuditRecorder에 있다. */
  private void audit(String action, UUID targetId, String message) {
    auditRecorder.record("ConsumableItem", action, targetId, message);
  }
}
