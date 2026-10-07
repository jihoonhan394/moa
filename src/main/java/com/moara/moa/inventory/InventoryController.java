package com.moara.moa.inventory;

import com.moara.moa.audit.TenantAuditRecorder;
import com.moara.moa.category.CategoryDomain;
import com.moara.moa.category.CategoryService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.support.Values;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
  private final InventoryCategoryRule categoryRule;
  private final InventoryCustodyService custodyService;
  private final InventoryPartService partService;
  private final CsvColumnMapper csvColumnMapper;
  private final TenantAuditRecorder auditRecorder;
  private final TenantContext tenantContext;

  public InventoryController(
      InventoryItemService inventoryService, InventoryImportService importService,
      ManagedUserService userService, com.moara.moa.group.AccessGroupService groupService,
      CategoryService categoryService, InventoryCategoryRule categoryRule,
      InventoryCustodyService custodyService,
      InventoryPartService partService, CsvColumnMapper csvColumnMapper,
      TenantAuditRecorder auditRecorder, TenantContext tenantContext) {
    this.inventoryService = inventoryService;
    this.importService = importService;
    this.userService = userService;
    this.groupService = groupService;
    this.categoryService = categoryService;
    this.categoryRule = categoryRule;
    this.custodyService = custodyService;
    this.partService = partService;
    this.csvColumnMapper = csvColumnMapper;
    this.auditRecorder = auditRecorder;
    this.tenantContext = tenantContext;
  }

  /** 선택 항목으로 넘어온 식별자. 안 고르거나 망가진 값은 안 고른 것으로 본다. */
  private static UUID parseUuid(String value) {
    return Values.optionalUuid(value);
  }

  /**
   * 한 사람이 지금 갖고 있는 자산. <b>퇴사 반납 체크리스트가 이 화면이다.</b>
   *
   * <p>0.9.4에서 퇴사 처리가 "창고 입고"를 적지 않게 바꿨다 — 아무도 물건을 보지 않았으니
   * 적을 수 없다. 그래서 반납 대기가 생기는데, 그것을 <b>사람 기준으로 모아 볼 화면이
   * 없었다.</b> 자산 목록에서 이름으로 훑어야 했다.
   *
   * <p>경로가 자산 쪽에 있는 이유: 자산 데이터는 자산 권한으로 본다
   * ({@code SecurityConfig}의 {@code /inventory/**} = ASSET_MANAGER). 사용자 관리 경로에
   * 두면 TENANT_ADMIN만 볼 수 있어, 정작 실물을 확인하는 사람이 못 본다.
   */
  @GetMapping("/inventory/by-user/{userId}")
  public String byUser(@PathVariable UUID userId, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    ManagedUser holder = userService.findById(tenantId, userId);
    List<InventoryItem> items = inventoryService.findAssignedTo(tenantId, userId);

    // 부품은 상위 장비에 묶여 움직이므로 따로 세지 않는다 — 노트북을 반납하면 안의 RAM도 온다.
    List<InventoryItem> topLevel = items.stream().filter(i -> !i.isPart()).toList();
    model.addAttribute("holder", holder);
    model.addAttribute("items", topLevel);
    model.addAttribute("returnPendingCount", topLevel.stream()
        .filter(i -> i.getStatus() == InventoryItemStatus.RETURN_PENDING).count());
    // 언제부터 갖고 있나. 분쟁이 날 때 이 날짜가 답한다.
    Map<UUID, LocalDate> heldSince = new LinkedHashMap<>();
    Map<UUID, Boolean> confirmed = new LinkedHashMap<>();
    for (InventoryItem item : topLevel) {
      custodyService.current(tenantId, item.getId()).ifPresent(custody -> {
        heldSince.put(item.getId(), custody.getStartedOn());
        confirmed.put(item.getId(), custody.getConfirmedAt() != null);
      });
    }
    model.addAttribute("heldSince", heldSince);
    model.addAttribute("confirmed", confirmed);
    model.addAttribute("currentMenu", "inventory");
    model.addAttribute("page", "inventory");
    return "inventory/by-user";
  }

  /**
   * 자산 목록. 기본은 <b>폐기품을 숨긴다</b> — 대장은 시간이 갈수록 폐기품이 쌓이는데,
   * 지금 쓰는 자산을 찾으러 온 사람에게 그것들이 섞여 보이면 목록이 쓸모를 잃는다.
   * 폐기 이력이 필요할 때만 켜서 본다(지우는 것이 아니라 가리는 것이다).
   */
  @GetMapping("/inventory")
  public String list(
      @RequestParam(name = "showRetired", required = false, defaultValue = "false")
      boolean showRetired,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) InventoryItemStatus status,
      @RequestParam(required = false) InventoryItemType type,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    if (!model.containsAttribute("inventoryForm")) {
      model.addAttribute("inventoryForm", new InventoryItemForm(null, null, null, null, null, null));
    }
    populate(model, showRetired, q, status, type, page);
    return "inventory/list";
  }

  @PostMapping("/inventory")
  public String create(
      @Valid @ModelAttribute("inventoryForm") InventoryItemForm form, BindingResult binding,
      @RequestParam(required = false) String ownerGroupId,
      @RequestParam(required = false) String parentItemId, Model model) {
    InventoryCategoryRule.Resolution category =
        categoryRule.resolve(tenantContext.currentTenantId(), form.category());
    if (!category.ok()) {
      binding.rejectValue("category", "category.invalid", category.error());
    }
    if (binding.hasErrors()) {
      populate(model);
      return "inventory/list";
    }
    InventoryItemForm form2 = resolved(form, category);
    try {
      InventoryItem created = inventoryService.create(tenantContext.currentTenantId(), form2);
      UUID owner = parseUuid(ownerGroupId);
      if (owner != null) {
        inventoryService.assignOwnerGroup(tenantContext.currentTenantId(), created.getId(), owner);
      }
      audit("INVENTORY_CREATE", created.getId(), created.getName());
      // 등록하면서 바로 장착. 없으면 ①등록 ②장비 열기 ③장착 세 단계를 거쳐야 한다.
      UUID parent = parseUuid(parentItemId);
      if (parent != null) {
        try {
          partService.attach(tenantContext.currentTenantId(), created.getId(), parent,
              created.getQuantity(), tenantContext.currentUserId());
          audit("INVENTORY_PART_ATTACH", parent, "등록 시 장착 — " + created.getName());
        } catch (IllegalArgumentException rejected) {
          // 등록 자체는 끝났으므로 되돌리지 않는다. 장착만 실패했다고 알린다.
          audit("INVENTORY_PART_ATTACH", parent, "등록 시 장착 실패 — " + rejected.getMessage());
        }
      }
      return "redirect:/inventory";
    } catch (DuplicateInventoryItemException exception) {
      binding.reject("inventory.duplicate", "이미 사용 중인 항목 이름입니다.");
      populate(model);
      return "inventory/list";
    }
  }

  /**
   * 등록 폼은 목록 화면 안에 있다. 다른 도메인(사용자·서버)이 {@code /new}를 쓰다 보니 주소를
   * 유추해 들어오는 경우가 있는데, 그러면 {@code {id}} 매핑과 엇갈려 <b>405가 떴다</b>.
   * 목록으로 보낸다 — 거기에 등록 폼이 있다.
   */
  @GetMapping("/inventory/new")
  public String newRedirect() {
    return "redirect:/inventory#new";
  }

  @GetMapping("/inventory/{id}/edit")
  public String editForm(@PathVariable UUID id, Model model) {
    InventoryItem item = inventoryService.findById(tenantContext.currentTenantId(), id);
    model.addAttribute("itemId", id);
    model.addAttribute("inventoryForm", new InventoryItemForm(
        item.getName(), item.getType(), item.getCategory(), item.getSerialNo(),
        item.getExpiresAt(), item.getPurchaseDate(), item.getWarrantyEnds(), item.getLeaseEnds(),
        item.getNote(), item.getQuantity()));
    model.addAttribute("assetCategories", assetCategoryNodes());
    return "inventory/form";
  }

  @PostMapping("/inventory/{id}")
  public String update(
      @PathVariable UUID id, @Valid @ModelAttribute("inventoryForm") InventoryItemForm form,
      BindingResult binding, Model model) {
    InventoryCategoryRule.Resolution category =
        categoryRule.resolve(tenantContext.currentTenantId(), form.category());
    if (!category.ok()) {
      binding.rejectValue("category", "category.invalid", category.error());
    }
    if (binding.hasErrors()) {
      model.addAttribute("itemId", id);
      model.addAttribute("assetCategories", assetCategoryNodes());
      return "inventory/form";
    }
    try {
      inventoryService.update(
          tenantContext.currentTenantId(), id, resolved(form, category));
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

  /**
   * 여러 자산을 한 번에 배정한다.
   *
   * <h2>왜 생겼나 — 목록 화면이 무거웠다</h2>
   *
   * 전에는 표의 <b>행마다</b> 사용자 드롭다운이 들어 있었다. 그래서 목록 HTML 크기가
   * {@code 행수 × 사용자수} 로 커졌다 — 실측 자산 130건·사용자 33명에서 option 3,952개,
   * <b>696KB</b> 였다. 자산 1,000건·사용자 100명이면 멈추는 쪽이다.
   *
   * <p>그리고 사람 입장에서도 느렸다. 자산 10건을 한 사람에게 주려면 <b>드롭다운을 10번</b>
   * 열어야 했다. 체크해서 한 번에 주는 쪽이 가볍고 빠르다.
   *
   * <p>⚠️ 한 건이 실패해도 나머지는 진행한다. 배정은 건별로 독립이고, 중간에 멈추면
   * "어디까지 됐나"를 사람이 다시 확인해야 한다. 실패 건수는 안내로 돌려준다.
   */
  @PostMapping("/inventory/bulk/assign")
  public String bulkAssign(
      @RequestParam(required = false) List<UUID> itemIds,
      @RequestParam UUID userId,
      RedirectAttributes redirectAttributes) {
    if (itemIds == null || itemIds.isEmpty()) {
      redirectAttributes.addFlashAttribute("bulkError", "먼저 자산을 선택하세요.");
      return "redirect:/inventory";
    }
    UUID tenantId = tenantContext.currentTenantId();
    int done = 0;
    List<String> failed = new ArrayList<>();
    for (UUID itemId : itemIds) {
      try {
        inventoryService.assign(tenantId, itemId, userId, tenantContext.currentUserId());
        audit("INVENTORY_ASSIGN", itemId, "user=" + userId + " (일괄)");
        done++;
      } catch (RuntimeException exception) {
        failed.add(itemId.toString());
      }
    }
    flashBulkResult(redirectAttributes, done + "건을 배정했습니다.", failed);
    return "redirect:/inventory";
  }

  /** 여러 자산의 소유팀을 한 번에 바꾼다. 비우면 해제. */
  @PostMapping("/inventory/bulk/owner")
  public String bulkOwner(
      @RequestParam(required = false) List<UUID> itemIds,
      @RequestParam(required = false) String ownerGroupId,
      RedirectAttributes redirectAttributes) {
    if (itemIds == null || itemIds.isEmpty()) {
      redirectAttributes.addFlashAttribute("bulkError", "먼저 자산을 선택하세요.");
      return "redirect:/inventory";
    }
    UUID tenantId = tenantContext.currentTenantId();
    UUID groupId = parseUuid(ownerGroupId);
    int done = 0;
    List<String> failed = new ArrayList<>();
    for (UUID itemId : itemIds) {
      try {
        inventoryService.assignOwnerGroup(tenantId, itemId, groupId);
        audit("INVENTORY_SET_OWNER", itemId, (groupId == null ? "해제" : "group=" + groupId) + " (일괄)");
        done++;
      } catch (RuntimeException exception) {
        failed.add(itemId.toString());
      }
    }
    flashBulkResult(redirectAttributes,
        done + "건의 소유팀을 " + (groupId == null ? "해제" : "변경") + "했습니다.", failed);
    return "redirect:/inventory";
  }

  /** 일괄 작업 결과 안내. 실패한 id 는 화면에 쏟지 않는다 — 건수와 다음 행동만 알려 준다. */
  private static void flashBulkResult(
      RedirectAttributes redirectAttributes, String message, List<String> failed) {
    if (failed.isEmpty()) {
      redirectAttributes.addFlashAttribute("bulkMessage", message);
      return;
    }
    redirectAttributes.addFlashAttribute("bulkError",
        message + " " + failed.size() + "건은 상태가 맞지 않아 건너뛰었습니다(이미 배정·폐기 등).");
  }

  @PostMapping("/inventory/{id}/assign")
  public String assign(@PathVariable UUID id, @RequestParam UUID userId) {
    inventoryService.assign(tenantContext.currentTenantId(), id, userId, tenantContext.currentUserId());
    audit("INVENTORY_ASSIGN", id, "user=" + userId);
    return "redirect:/inventory";
  }

  @PostMapping("/inventory/{id}/reclaim")
  public String reclaim(@PathVariable UUID id) {
    inventoryService.reclaim(tenantContext.currentTenantId(), id, tenantContext.currentUserId(), "회수");
    audit("INVENTORY_RECLAIM", id, null);
    return "redirect:/inventory";
  }

  @PostMapping("/inventory/{id}/retire")
  public String retire(@PathVariable UUID id) {
    inventoryService.retire(tenantContext.currentTenantId(), id, tenantContext.currentUserId());
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
    // 열 순서가 우리 형식과 다를 수 있으므로 바로 태우지 않고 매핑을 확인받는다.
    // "이대로 맞다"고 사람이 눌러야 등록된다 — AI든 규칙이든 제안일 뿐이다.
    List<List<String>> rows = CsvColumnMapper.parseRows(csv);
    if (rows.isEmpty()) {
      model.addAttribute("error", "읽을 수 있는 행이 없습니다.");
      return importForm(model);
    }
    boolean hasHeader = CsvColumnMapper.looksLikeHeader(rows.get(0));
    List<String> headers = hasHeader ? rows.get(0)
        : java.util.stream.IntStream.range(0, rows.get(0).size())
            .mapToObj(i -> (i + 1) + "번째 열").toList();
    List<List<String>> samples = rows.subList(hasHeader ? 1 : 0, Math.min(rows.size(), hasHeader ? 4 : 3));
    model.addAttribute("mappings",
        csvColumnMapper.propose(tenantContext.currentTenantId(), headers, samples));
    model.addAttribute("headers", headers);
    model.addAttribute("samples", samples);
    model.addAttribute("hasHeader", hasHeader);
    model.addAttribute("rawCsv", csv);
    model.addAttribute("rowCount", rows.size() - (hasHeader ? 1 : 0));
    model.addAttribute("page", "inventory");
    model.addAttribute("pageTitle", "자산 대량 등록");
    model.addAttribute("projectName", "MOA");
    return "inventory/import-mapping";
  }

  /**
   * 매핑을 확정하고 실제로 등록한다. 값은 손대지 않고 <b>자리만 옮겨</b> 기존 임포트에 태운다
   * — 검증·중복 판정은 그대로 한 곳에 있다.
   */
  @PostMapping("/inventory/import/confirm")
  public String importConfirm(
      @RequestParam String rawCsv, @RequestParam(defaultValue = "false") boolean hasHeader,
      @RequestParam Map<String, String> params, Model model) {
    Map<String, Integer> mapping = new java.util.LinkedHashMap<>();
    for (String target : CsvColumnMapper.targets()) {
      String raw = params.get("map_" + target);
      if (raw != null && !raw.isBlank()) {
        try {
          mapping.put(target, Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
          // 값이 깨졌으면 그 열은 비운 것으로 본다
        }
      }
    }
    if (!mapping.containsKey("이름")) {
      model.addAttribute("error", "'이름' 열은 반드시 지정해야 합니다.");
      return importForm(model);
    }
    List<List<String>> rows = CsvColumnMapper.parseRows(rawCsv);
    List<List<String>> data = hasHeader && !rows.isEmpty() ? rows.subList(1, rows.size()) : rows;
    String normalized = csvColumnMapper.rearrange(data, mapping);
    InventoryImportResult result =
        importService.importCsv(tenantContext.currentTenantId(), normalized);
    audit("INVENTORY_IMPORT", null,
        "created=" + result.created() + " failed=" + result.errors().size());
    model.addAttribute("result", result);
    model.addAttribute("page", "inventory");
    model.addAttribute("pageTitle", "자산 대량 등록");
    model.addAttribute("projectName", "MOA");
    return "inventory/import";
  }

  private void populate(Model model) {
    populate(model, false, null, null, null, 0);
  }

  private void populate(Model model, boolean showRetired) {
    populate(model, showRetired, null, null, null, 0);
  }

  private void populate(Model model, boolean showRetired, String keyword,
      InventoryItemStatus status, InventoryItemType type, int page) {
    UUID tenantId = tenantContext.currentTenantId();
    // 거르기와 자르기를 모두 DB 가 한다. 전체를 읽어 stream 으로 거르면 자산이 수천 건인
    // 기관에서 매 요청마다 테이블을 통째로 읽는다 — 감사 로그에서 고친 것과 같은 패턴이다.
    var found = inventoryService.filter(tenantId, keyword, status, type, showRetired, page);
    model.addAttribute("items", found.getContent());
    // 페이지를 넘길 때 검색·필터 조건이 함께 따라가야 한다 — 안 그러면 2페이지에서 조건이 풀린다.
    model.addAttribute("pager", com.moara.moa.web.support.PageView.of(found, "/inventory",
        com.moara.moa.web.support.PageView.conditions(
            "q", keyword, "status", status, "type", type,
            "showRetired", showRetired ? "true" : null)));
    model.addAttribute("showRetired", showRetired);
    model.addAttribute("q", keyword);
    model.addAttribute("status", status);
    model.addAttribute("type", type);
    model.addAttribute("statuses", InventoryItemStatus.values());
    model.addAttribute("types", InventoryItemType.values());
    // 등록 화면에서 "어느 장비에 넣을지" 고를 수 있게(장착 안 하면 비워 둔다).
    // 여기는 **필터와 무관**하다 — 검색 중에도 장착 대상은 전체에서 골라야 한다.
    model.addAttribute("attachTargets", inventoryService.findAll(tenantId).stream()
        .filter(i -> i.getStatus() != InventoryItemStatus.RETIRED)
        .filter(i -> !i.isPart())
        .toList());
    model.addAttribute("retiredCount", inventoryService.retiredCount(tenantId));
    // 배정했는데 받은 사람이 아직 "받았다"를 안 누른 것. 담당자가 챙길 수 있게 목록에 배지로.
    model.addAttribute("awaitingConfirmIds", custodyService.awaitingConfirmation(tenantId).stream()
        .map(InventoryCustody::getItemId).collect(java.util.stream.Collectors.toSet()));
    // 퇴사했는데 실물이 안 돌아온 것. 가장 잃어버리기 쉬운 자산이라 목록 맨 위에서 세어 준다.
    model.addAttribute("returnPendingCount", inventoryService.returnPending(tenantId).size());
    model.addAttribute("assetCategories", assetCategoryNodes());
    java.util.List<ManagedUser> tenantUsers = userService.findByTenant(tenantId);
    model.addAttribute("tenantUsers", tenantUsers);
    Map<UUID, String> userNames = new LinkedHashMap<>();
    for (ManagedUser u : tenantUsers) {
      userNames.put(u.getId(), ManagedUserService.label(u));
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

  /**
   * 판정 결과(유형 + <b>해석된 경로</b>)를 폼에 싣는다.
   *
   * <p>경로를 덮어쓰는 이유: 짧은 이름을 받았을 때 저장할 값은 입력이 아니라 해석된 전체
   * 경로다. 입력을 그대로 두면 분류 필터에 안 걸리는, 고치려던 바로 그 상태가 된다.
   */
  private static InventoryItemForm resolved(
      InventoryItemForm form, InventoryCategoryRule.Resolution category) {
    return new InventoryItemForm(form.name(), category.type(), category.path(), form.serialNo(),
        form.expiresAt(), form.purchaseDate(), form.warrantyEnds(), form.leaseEnds(), form.note(),
        form.quantity());
  }

  /** 특권 행위 기록. 정책(행위자 없으면 미기록 등)은 TenantAuditRecorder에 있다. */
  private void audit(String action, UUID targetId, String message) {
    auditRecorder.record("InventoryItem", action, targetId, message);
  }
}
