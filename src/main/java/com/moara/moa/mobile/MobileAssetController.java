package com.moara.moa.mobile;

import com.moara.moa.audit.TenantAuditRecorder;
import com.moara.moa.category.CategoryService;
import com.moara.moa.inventory.InventoryItem;
import com.moara.moa.inventory.InventoryItemForm;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.inventory.InventoryCategoryRule;
import com.moara.moa.inventory.InventoryItemType;
import com.moara.moa.inventory.tag.AssetTag;
import com.moara.moa.inventory.tag.AssetTagCode;
import com.moara.moa.inventory.tag.AssetTagNotFoundException;
import com.moara.moa.inventory.tag.AssetTagService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUserService;
import jakarta.validation.Valid;
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

/**
 * 모바일 전용 화면(`/m/**`).
 *
 * <p>PC 화면을 좁게 줄인 것이 아니다. <b>현장에서 30초 안에 끝나는 일</b>만 둔다 — 코드를 찍어
 * 자산을 보거나, 그 자리에서 등록하거나, 내 것을 확인하는 것.
 *
 * <p>이 경로가 따로 있어야 DMZ 리버스 프록시에서 <b>경로 화이트리스트</b>가 성립한다. 지금 URL 을
 * 그대로 열면 볼트·대장 전체·CSV 임포트까지 같이 열린다.
 *
 * <p>⚠️ User-Agent 의 {@code caps=} 는 <b>표시용</b>이다. 인가는 역할로만 판단한다. 클라이언트가
 * 보낸 값을 신뢰하지 않는다는 규칙(서버 AGENTS.md)이 그대로 적용된다.
 */
@Controller
public class MobileAssetController {

  private static final String AUDIT_DOMAIN = "자산대장";

  private final AssetTagService tagService;
  private final InventoryItemService inventoryService;
  private final CategoryService categoryService;
  private final InventoryCategoryRule categoryRule;
  private final ManagedUserService userService;
  private final TenantAuditRecorder auditRecorder;
  private final TenantContext tenantContext;
  private final com.moara.moa.notification.NotificationService notificationService;
  private final MobileInbox inbox;
  private final com.moara.moa.solution.SolutionHealthService healthService;

  public MobileAssetController(
      AssetTagService tagService,
      InventoryItemService inventoryService,
      CategoryService categoryService,
      InventoryCategoryRule categoryRule,
      ManagedUserService userService,
      TenantAuditRecorder auditRecorder,
      TenantContext tenantContext,
      com.moara.moa.notification.NotificationService notificationService,
      MobileInbox inbox,
      com.moara.moa.solution.SolutionHealthService healthService) {
    this.tagService = tagService;
    this.inventoryService = inventoryService;
    this.categoryService = categoryService;
    this.categoryRule = categoryRule;
    this.userService = userService;
    this.auditRecorder = auditRecorder;
    this.tenantContext = tenantContext;
    this.notificationService = notificationService;
    this.inbox = inbox;
    this.healthService = healthService;
  }

  /**
   * 홈. 현장에서 가장 많이 누르는 것(스캔) 하나만 크게 두고, 나머지는 <b>숫자가 붙은 줄</b>이다.
   *
   * <p>숫자가 없으면 열어 봐야 알 수 있고, 그러면 아무도 안 연다.
   */
  @GetMapping("/m")
  public String home(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID me = tenantContext.currentUserId();
    model.addAttribute("myItemCount", inventoryService.findAssignedTo(tenantId, me).size());
    model.addAttribute("unattachedCount", tagService.unattachedCount(tenantId));
    model.addAttribute("unreadCount", notificationService.unreadCount(tenantId, me));
    model.addAttribute("approvalCount", inbox.pendingFor(tenantId, me).total());
    // 솔루션은 인프라 관리자에게만 뜻이 있다. 일반 사용자에게는 줄을 만들지 않는다.
    boolean showSolutions =
        tenantContext.currentUser().hasRole(com.moara.moa.user.UserRole.INFRA_MANAGER);
    model.addAttribute("showSolutions", showSolutions);
    // 장애 수는 그 줄을 띄우는 사람에게만 센다 — 안 보여 줄 숫자를 세느라 질의를 더 하지 않는다.
    model.addAttribute("downCount", showSolutions ? healthService.downCount(tenantId) : 0L);
    model.addAttribute("currentMenu", "home");
    return "mobile/home";
  }

  /** 코드 입력. 앱에서는 카메라가 이 자리를 대신하고, 안 될 때 손으로 칠 수 있게 남겨 둔다. */
  @GetMapping("/m/scan")
  public String scan(@RequestParam(required = false) String notFound, Model model) {
    model.addAttribute("notFound", notFound);
    model.addAttribute("currentMenu", "scan");
    return "mobile/scan";
  }

  @PostMapping("/m/scan")
  public String submitCode(@RequestParam String code) {
    String normalized = AssetTagCode.normalize(code);
    if (normalized == null) {
      // 형식이 틀린 것과 없는 코드를 구별해 알려 주지 않는다.
      return "redirect:/m/scan?notFound=" + java.net.URLEncoder.encode(
          code == null ? "" : code.trim(), java.nio.charset.StandardCharsets.UTF_8);
    }
    return "redirect:/m/t/" + normalized;
  }

  /**
   * 찍은 코드 한 장. <b>사용자가 모드를 고르지 않는다</b> — 붙어 있으면 조회, 비어 있으면 등록이다.
   */
  @GetMapping("/m/t/{code}")
  public String tag(@PathVariable String code, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    AssetTag tag;
    try {
      tag = tagService.resolve(tenantId, code);
    } catch (AssetTagNotFoundException notFound) {
      return "redirect:/m/scan?notFound=" + java.net.URLEncoder.encode(
          code, java.nio.charset.StandardCharsets.UTF_8);
    }

    model.addAttribute("tag", tag);
    model.addAttribute("currentMenu", "scan");
    if (!tag.isAttached()) {
      model.addAttribute("form", new InventoryItemForm(null, null, null, null, null, null));
      model.addAttribute("categories", categoryService.assetTree(tenantId));
      return "mobile/tag-register";
    }

    InventoryItem item = inventoryService.findById(tenantId, tag.getItemId());
    model.addAttribute("item", item);
    model.addAttribute("holder", holderLabel(tenantId, item));
    return "mobile/tag-item";
  }

  /** 비어 있는 코드에 자산을 등록하고 그 자리에서 붙인다. */
  @PostMapping("/m/t/{code}")
  public String register(
      @PathVariable String code,
      @Valid @ModelAttribute("form") InventoryItemForm form,
      BindingResult binding,
      Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    AssetTag tag = tagService.resolve(tenantId, code);
    if (tag.isAttached()) {
      return "redirect:/m/t/" + tag.getCode();
    }
    // 분류 판정과 유형 파생은 InventoryCategoryRule 한 곳에 있다 — PC 등록·수정·CSV 임포트와
    // 같은 규칙이다. 넷이 각자 검사하다 실제로 어긋났었다(PC 는 최상위만 봤다).
    InventoryCategoryRule.Resolution category = categoryRule.resolve(tenantId, form.category());
    if (!category.ok()) {
      binding.rejectValue("category", "category.invalid", category.error());
    }
    if (binding.hasErrors()) {
      model.addAttribute("tag", tag);
      model.addAttribute("categories", categoryService.assetTree(tenantId));
      model.addAttribute("currentMenu", "scan");
      return "mobile/tag-register";
    }

    InventoryItem created = inventoryService.create(tenantId, resolved(form, category));
    tagService.attach(tenantId, tag.getCode(), created.getId(), tenantContext.currentUserId());
    audit("INVENTORY_CREATE", created.getId(), "모바일 등록 " + tag.getCode() + " " + created.getName());
    return "redirect:/m/t/" + tag.getCode();
  }

  /**
   * 이미 등록된 자산에 번호를 붙인다.
   *
   * <p>현장의 대부분은 <b>새 자산 등록이 아니라 기존 자산에 스티커를 붙이는 일</b>이다. 대장에
   * 이미 수백 건이 있고 거기에 번호를 달아 가는 것이 실제 작업이다. 이 경로가 없으면 기존 자산은
   * 영영 번호를 못 받는다.
   */
  @GetMapping("/m/t/{code}/attach")
  public String attachForm(
      @PathVariable String code, @RequestParam(required = false) String q,
      @RequestParam(required = false) String already, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    AssetTag tag = tagService.resolve(tenantId, code);
    if (tag.isAttached()) {
      return "redirect:/m/t/" + tag.getCode();
    }
    // 붙일 수 있는 것만 보여 준다. 이미 번호가 있는 자산은 고를 수 없으므로 자리만 차지한다.
    List<InventoryItem> attachable = inventoryService.searchAttachable(tenantId, q);
    model.addAttribute("tag", tag);
    model.addAttribute("q", q);
    model.addAttribute("items", attachable);
    // 하나도 없을 때만 "이미 번호가 붙어서 안 보이는 것인지"를 따져 안내한다.
    model.addAttribute("allTagged",
        attachable.isEmpty() && !inventoryService.search(tenantId, q).isEmpty());
    model.addAttribute("already", already);
    model.addAttribute("currentMenu", "scan");
    return "mobile/tag-attach";
  }

  @PostMapping("/m/t/{code}/attach")
  public String attach(@PathVariable String code, @RequestParam UUID itemId) {
    UUID tenantId = tenantContext.currentTenantId();
    AssetTag tag = tagService.resolve(tenantId, code);
    InventoryItem item = inventoryService.findById(tenantId, itemId);
    try {
      tagService.attach(tenantId, tag.getCode(), item.getId(), tenantContext.currentUserId());
    } catch (com.moara.moa.inventory.tag.ItemAlreadyTaggedException already) {
      // 이미 붙어 있는 번호를 알려 준다 — 현장에서는 "그럼 이 장비 번호가 뭔데?" 가 다음 질문이다.
      return "redirect:/m/t/" + tag.getCode() + "/attach?already=" + already.getExistingCode();
    }
    audit("ASSET_TAG_ATTACH", item.getId(), tag.getCode() + " → " + item.getName());
    return "redirect:/m/t/" + tag.getCode();
  }

  /** 내가 갖고 있는 것. 전 직원이 쓰는 화면이라 가장 단순하게 둔다. */
  @GetMapping("/m/me")
  public String mine(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    List<InventoryItem> items = inventoryService.findAssignedTo(tenantId, tenantContext.currentUserId());
    model.addAttribute("items", items);
    model.addAttribute("codes", tagService.codesFor(tenantId, items.stream().map(InventoryItem::getId).toList()));
    model.addAttribute("currentMenu", "me");
    return "mobile/mine";
  }

  /** 유형과 <b>해석된 경로</b>를 폼에 싣는다. 짧은 이름을 받았으면 전체 경로로 저장한다. */
  private static InventoryItemForm resolved(
      InventoryItemForm form, InventoryCategoryRule.Resolution category) {
    return new InventoryItemForm(form.name(), category.type(),
        category.path(), form.serialNo(), form.expiresAt(), form.purchaseDate(),
        form.warrantyEnds(), form.leaseEnds(), form.note(), form.quantity());
  }

  private String holderLabel(UUID tenantId, InventoryItem item) {
    if (item.getAssignedUserId() == null) {
      return null;
    }
    return userService.labelsByTenant(tenantId).get(item.getAssignedUserId());
  }

  private void audit(String action, UUID targetId, String message) {
    auditRecorder.record(AUDIT_DOMAIN, action, targetId, message);
  }
}
