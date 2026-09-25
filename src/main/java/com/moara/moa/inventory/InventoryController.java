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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
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
  private final InventoryCustodyService custodyService;
  private final InventoryPartService partService;
  private final CsvColumnMapper csvColumnMapper;
  private final TenantAuditRecorder auditRecorder;
  private final TenantContext tenantContext;

  public InventoryController(
      InventoryItemService inventoryService, InventoryImportService importService,
      ManagedUserService userService, com.moara.moa.group.AccessGroupService groupService,
      CategoryService categoryService, InventoryCustodyService custodyService,
      InventoryPartService partService, CsvColumnMapper csvColumnMapper,
      TenantAuditRecorder auditRecorder, TenantContext tenantContext) {
    this.inventoryService = inventoryService;
    this.importService = importService;
    this.userService = userService;
    this.groupService = groupService;
    this.categoryService = categoryService;
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
   * 자산 목록. 기본은 <b>폐기품을 숨긴다</b> — 대장은 시간이 갈수록 폐기품이 쌓이는데,
   * 지금 쓰는 자산을 찾으러 온 사람에게 그것들이 섞여 보이면 목록이 쓸모를 잃는다.
   * 폐기 이력이 필요할 때만 켜서 본다(지우는 것이 아니라 가리는 것이다).
   */
  @GetMapping("/inventory")
  public String list(
      @RequestParam(name = "showRetired", required = false, defaultValue = "false")
      boolean showRetired, Model model) {
    if (!model.containsAttribute("inventoryForm")) {
      model.addAttribute("inventoryForm", new InventoryItemForm(null, null, null, null, null, null));
    }
    populate(model, showRetired);
    return "inventory/list";
  }

  @PostMapping("/inventory")
  public String create(
      @Valid @ModelAttribute("inventoryForm") InventoryItemForm form, BindingResult binding,
      @RequestParam(required = false) String ownerGroupId,
      @RequestParam(required = false) String parentItemId, Model model) {
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
    populate(model, false);
  }

  private void populate(Model model, boolean showRetired) {
    UUID tenantId = tenantContext.currentTenantId();
    List<InventoryItem> all = inventoryService.findAll(tenantId);
    long retired = all.stream()
        .filter(i -> i.getStatus() == InventoryItemStatus.RETIRED).count();
    model.addAttribute("items", showRetired ? all
        : all.stream().filter(i -> i.getStatus() != InventoryItemStatus.RETIRED).toList());
    model.addAttribute("showRetired", showRetired);
    // 등록 화면에서 "어느 장비에 넣을지" 고를 수 있게(장착 안 하면 비워 둔다).
    model.addAttribute("attachTargets", all.stream()
        .filter(i -> i.getStatus() != InventoryItemStatus.RETIRED)
        .filter(i -> !i.isPart())
        .toList());
    model.addAttribute("retiredCount", retired);
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
        form.expiresAt(), form.purchaseDate(), form.warrantyEnds(), form.leaseEnds(), form.note(),
        form.quantity());
  }

  /** 특권 행위 기록. 정책(행위자 없으면 미기록 등)은 TenantAuditRecorder에 있다. */
  private void audit(String action, UUID targetId, String message) {
    auditRecorder.record("InventoryItem", action, targetId, message);
  }
}
