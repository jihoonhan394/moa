package com.moara.moa.mobile;

import com.moara.moa.access.AccessApprovalService;
import com.moara.moa.access.AccessRequest;
import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetService;
import com.moara.moa.audit.TenantAuditRecorder;
import com.moara.moa.consumable.ConsumableService;
import com.moara.moa.consumable.ConsumableRequest;
import com.moara.moa.consumable.ConsumableRequestService;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.inventory.InventoryCustody;
import com.moara.moa.inventory.InventoryCustodyService;
import com.moara.moa.inventory.InventoryItem;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.notice.NoticeService;
import com.moara.moa.notification.NotificationService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.solution.HealthState;
import com.moara.moa.solution.ManagedSolution;
import com.moara.moa.solution.ManagedSolutionService;
import com.moara.moa.solution.SolutionHealth;
import com.moara.moa.solution.SolutionHealthService;
import com.moara.moa.user.ManagedUserService;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 모바일의 <b>승인 · 알림 · 내 것 · 연락처</b>.
 *
 * <p>자산(QR)은 {@link MobileAssetController} 가 맡는다. 여기는 "몸에 있어서 값어치가 있는 것"
 * 들이다 — 승인자가 PC 를 열 때까지 기다리지 않게 하고, 장애 때 누구에게 전화할지 바로 찾는다.
 *
 * <p>⚠️ <b>솔루션을 제어하지 않는다.</b> 상태와 연락처만 본다(moa-mobile doc/01).
 */
@Controller
public class MobileWorkController {

  private static final String AUDIT_DOMAIN = "모바일";

  private final AccessApprovalService approvalService;
  private final ConsumableRequestService consumableRequestService;
  private final ConsumableService consumableItemService;
  private final InventoryCustodyService custodyService;
  private final InventoryItemService inventoryService;
  private final NotificationService notificationService;
  private final NoticeService noticeService;
  private final ManagedSolutionService solutionService;
  private final SolutionHealthService healthService;
  private final AssetService assetService;
  private final AccessGroupService groupService;
  private final ManagedUserService userService;
  private final MobileInbox inbox;
  private final TenantAuditRecorder auditRecorder;
  private final TenantContext tenantContext;

  public MobileWorkController(
      AccessApprovalService approvalService,
      ConsumableRequestService consumableRequestService,
      ConsumableService consumableItemService,
      InventoryCustodyService custodyService,
      InventoryItemService inventoryService,
      NotificationService notificationService,
      NoticeService noticeService,
      ManagedSolutionService solutionService,
      SolutionHealthService healthService,
      AssetService assetService,
      AccessGroupService groupService,
      ManagedUserService userService,
      MobileInbox inbox,
      TenantAuditRecorder auditRecorder,
      TenantContext tenantContext) {
    this.approvalService = approvalService;
    this.consumableRequestService = consumableRequestService;
    this.consumableItemService = consumableItemService;
    this.custodyService = custodyService;
    this.inventoryService = inventoryService;
    this.notificationService = notificationService;
    this.noticeService = noticeService;
    this.solutionService = solutionService;
    this.healthService = healthService;
    this.assetService = assetService;
    this.groupService = groupService;
    this.userService = userService;
    this.inbox = inbox;
    this.auditRecorder = auditRecorder;
    this.tenantContext = tenantContext;
  }

  // ── 승인 ──────────────────────────────────────────────────────────────

  /**
   * 내가 처리해야 하는 것 세 가지를 한 화면에 모은다.
   *
   * <p>따로 두면 "어디에 뭐가 쌓였는지" 확인하러 세 번 들어가야 한다. 폰에서 30초 안에 끝나야
   * 하는 화면이라 그렇게 두면 안 쓴다.
   */
  @GetMapping("/m/approvals")
  public String approvals(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID me = tenantContext.currentUserId();

    // 무엇이 내 일감인지는 MobileInbox 가 정한다 — 홈 뱃지도 같은 것을 센다.
    MobileInbox.Pending pending = inbox.pendingFor(tenantId, me);
    List<AccessRequest> access = pending.access();
    List<ConsumableRequest> supplies = pending.supplies();
    List<InventoryCustody> receipts = pending.receipts();

    model.addAttribute("accessRequests", access);
    model.addAttribute("assetNames", assetNames(tenantId, access));
    model.addAttribute("requesterNames", userService.labelsByTenant(tenantId));
    model.addAttribute("supplyRequests", supplies);
    model.addAttribute("supplyNames", supplyNames(tenantId, supplies));
    model.addAttribute("receipts", receipts);
    model.addAttribute("receiptNames", receiptNames(tenantId, receipts));
    model.addAttribute("total", pending.total());
    model.addAttribute("currentMenu", "approvals");
    return "mobile/approvals";
  }

  @PostMapping("/m/approvals/access/{id}/approve")
  public String approveAccess(@PathVariable UUID id, @RequestParam(required = false) String comment) {
    UUID tenantId = tenantContext.currentTenantId();
    // 자격증명은 폰에서 고르지 않는다 — 볼트를 모바일에 끌어들이지 않는다(doc/01).
    approvalService.approve(tenantId, id, tenantContext.currentUserId(), null, comment);
    audit("ACCESS_REQUEST_APPROVE", id, "모바일 승인");
    return "redirect:/m/approvals";
  }

  @PostMapping("/m/approvals/access/{id}/reject")
  public String rejectAccess(@PathVariable UUID id, @RequestParam(required = false) String comment) {
    UUID tenantId = tenantContext.currentTenantId();
    approvalService.reject(tenantId, id, tenantContext.currentUserId(), comment);
    audit("ACCESS_REQUEST_REJECT", id, "모바일 거절");
    return "redirect:/m/approvals";
  }

  @PostMapping("/m/approvals/supply/{id}/ack")
  public String acknowledgeSupply(@PathVariable UUID id) {
    UUID tenantId = tenantContext.currentTenantId();
    // ⚠️ ConsumableRequestService.acknowledge 에는 권한 검사가 없다(PC 컨트롤러가 하고 있다).
    // 여기서 하지 않으면 누구나 남의 요청을 접수 처리할 수 있다.
    requireSupplyHandler();
    consumableRequestService.acknowledge(tenantId, id, tenantContext.currentUserId());
    audit("CONSUMABLE_REQUEST_ACK", id, "모바일 확인");
    return "redirect:/m/approvals";
  }

  /** 내가 받은 물건이 맞다고 확인한다. 받은 사람만 누를 수 있다(서비스가 막는다). */
  @PostMapping("/m/approvals/receipt/{itemId}/confirm")
  public String confirmReceipt(@PathVariable UUID itemId) {
    UUID tenantId = tenantContext.currentTenantId();
    boolean confirmed = custodyService.confirmReceipt(tenantId, itemId, tenantContext.currentUserId());
    if (confirmed) {
      audit("INVENTORY_CUSTODY_CONFIRM", itemId, "모바일 인수 확인");
    }
    return "redirect:/m/approvals";
  }

  /** 소모품 요청을 처리할 수 있는 사람인가. PC 화면(MyConsumableController)과 같은 기준이다. */
  private void requireSupplyHandler() {
    if (!inbox.canHandleSupplies()) {
      throw new org.springframework.security.access.AccessDeniedException("소모품 요청 처리 권한이 없습니다.");
    }
  }

  // ── 알림 ──────────────────────────────────────────────────────────────

  @GetMapping("/m/notifications")
  public String notifications(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID me = tenantContext.currentUserId();
    // 링크는 PC 경로로 저장된다. 폰에서 누르면 PC 화면이 떠 글자가 깨알같아지므로
    // 모바일 대응 화면이 있는 것만 /m/** 로 바꿔 띄운다({@link MobileLinks}).
    List<NotificationRow> items = notificationService.list(tenantId, me).stream()
        .map(n -> new NotificationRow(n.getTitle(), n.getBody(), MobileLinks.toMobile(n.getLink())))
        .toList();
    model.addAttribute("items", items);
    model.addAttribute("currentMenu", "home");
    return "mobile/notifications";
  }

  /** 알림 한 줄. 엔티티를 그대로 넘기지 않는 이유는 링크를 모바일 경로로 바꿔야 해서다. */
  public record NotificationRow(String title, String body, String link) {}

  @PostMapping("/m/notifications/read-all")
  public String readAll() {
    notificationService.markAllRead(tenantContext.currentTenantId(), tenantContext.currentUserId());
    return "redirect:/m/notifications";
  }

  // ── 상태 · 연락처 ─────────────────────────────────────────────────────

  /**
   * 솔루션 목록. <b>제어 버튼은 없다</b> — 상태와 연락처만 본다.
   *
   * <p><b>장애가 위로 온다.</b> 폰에서 이 화면을 여는 순간은 거의 항상 "뭔가 멈췄다"일 때이고,
   * 그때 24개 중 멈춘 하나를 찾아 스크롤하게 두면 안 된다. 정렬 기준은 상태(DOWN→UNKNOWN→UP)
   * 다음에 이름이다 — 같은 상태 안에서 순서가 매번 바뀌면 눈이 위치를 기억하지 못한다.
   */
  @GetMapping("/m/solutions")
  public String solutions(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    Map<UUID, SolutionHealth> health = healthService.statesOf(tenantId);
    List<ManagedSolution> solutions = solutionService.findAll(tenantId).stream()
        .sorted(Comparator
            .comparingInt((ManagedSolution solution) -> severity(health.get(solution.getId())))
            .thenComparing(ManagedSolution::getName))
        .toList();
    model.addAttribute("solutions", solutions);
    model.addAttribute("health", health);
    model.addAttribute("downCount", healthService.downCount(tenantId));
    model.addAttribute("hostNames", hostNames(tenantId, solutions));
    model.addAttribute("currentMenu", "home");
    return "mobile/solutions";
  }

  /** 정렬 가중치. 작을수록 위다. 상태 행이 없는 솔루션(아직 안 재 봄)은 UNKNOWN 과 같이 둔다. */
  private static int severity(SolutionHealth health) {
    if (health == null) {
      return 1;
    }
    return switch (health.getState()) {
      case DOWN -> 0;
      case UNKNOWN -> 1;
      case UP -> 2;
    };
  }

  @GetMapping("/m/solutions/{id}")
  public String solution(@PathVariable UUID id, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    ManagedSolution solution = solutionService.findById(tenantId, id);
    model.addAttribute("solution", solution);
    model.addAttribute("host", hostOf(tenantId, solution));
    model.addAttribute("ownerGroupName", solution.getOwnerGroupId() == null ? null
        : groupService.findById(tenantId, solution.getOwnerGroupId()).getName());
    model.addAttribute("health", healthService.stateOf(tenantId, id));
    model.addAttribute("currentMenu", "home");
    return "mobile/solution";
  }

  // ── 내 것 ─────────────────────────────────────────────────────────────

  /** 내 요청 상태와 공지. 내 자산은 {@link MobileAssetController} 가 그린다. */
  @GetMapping("/m/me/requests")
  public String myRequests(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID me = tenantContext.currentUserId();
    List<AccessRequest> access = approvalService.myRequests(tenantId, me);
    List<ConsumableRequest> supplies = consumableRequestService.findMine(tenantId, me);
    model.addAttribute("accessRequests", access);
    model.addAttribute("assetNames", assetNames(tenantId, access));
    model.addAttribute("supplyRequests", supplies);
    model.addAttribute("supplyNames", supplyNames(tenantId, supplies));
    model.addAttribute("currentMenu", "me");
    return "mobile/my-requests";
  }

  @GetMapping("/m/notices")
  public String notices(Model model) {
    model.addAttribute("notices", noticeService.list(tenantContext.currentTenantId()));
    model.addAttribute("currentMenu", "me");
    return "mobile/notices";
  }

  // ── 이름 묶음 조회 (N+1 을 만들지 않는다) ─────────────────────────────

  private Map<UUID, String> assetNames(UUID tenantId, List<AccessRequest> requests) {
    if (requests.isEmpty()) {
      return Map.of();
    }
    Map<UUID, String> names = new LinkedHashMap<>();
    for (Asset asset : assetService.findAll(tenantId)) {
      names.put(asset.getId(), asset.getName());
    }
    return names;
  }

  private Map<UUID, String> supplyNames(UUID tenantId, List<ConsumableRequest> requests) {
    if (requests.isEmpty()) {
      return Map.of();
    }
    Map<UUID, String> names = new LinkedHashMap<>();
    consumableItemService.findAll(tenantId).forEach(item -> names.put(item.getId(), item.getName()));
    return names;
  }

  private Map<UUID, String> receiptNames(UUID tenantId, List<InventoryCustody> receipts) {
    if (receipts.isEmpty()) {
      return Map.of();
    }
    Map<UUID, String> names = new LinkedHashMap<>();
    for (InventoryItem item : inventoryService.findAll(tenantId)) {
      names.put(item.getId(), item.getName());
    }
    return names;
  }

  private Map<UUID, String> hostNames(UUID tenantId, List<ManagedSolution> solutions) {
    if (solutions.isEmpty()) {
      return Map.of();
    }
    Map<UUID, String> names = new LinkedHashMap<>();
    for (Asset asset : assetService.findAll(tenantId)) {
      names.put(asset.getId(), asset.getName());
    }
    return names;
  }

  private String hostOf(UUID tenantId, ManagedSolution solution) {
    if (solution.getAssetId() == null) {
      return null;
    }
    return assetService.findById(tenantId, solution.getAssetId()).getName();
  }

  private void audit(String action, UUID targetId, String message) {
    auditRecorder.record(AUDIT_DOMAIN, action, targetId, message);
  }
}
