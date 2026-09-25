package com.moara.moa.asset;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.TenantAuditRecorder;
import com.moara.moa.category.CategoryDomain;
import com.moara.moa.category.CategoryService;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUserService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AssetController {
  private static final String TARGET_TYPE = "Asset";

  private final AssetService assetService;
  private final AuditLogService auditLogService;
  private final TenantAuditRecorder auditRecorder;
  private final ManagedUserService userService;
  private final AccessGroupService groupService;
  private final CategoryService categoryService;
  private final TenantContext tenantContext;

  public AssetController(
      AssetService assetService, AuditLogService auditLogService, TenantAuditRecorder auditRecorder,
      ManagedUserService userService, AccessGroupService groupService,
      CategoryService categoryService, TenantContext tenantContext) {
    this.assetService = assetService;
    this.auditLogService = auditLogService;
    this.auditRecorder = auditRecorder;
    this.userService = userService;
    this.groupService = groupService;
    this.categoryService = categoryService;
    this.tenantContext = tenantContext;
  }

  private static UUID parseUuid(String value) {
    return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
  }

  @GetMapping("/assets")
  public String list(Model model) {
    model.addAttribute("assets", assetService.findAll(tenantContext.currentTenantId()));
    model.addAttribute("isServerPage", false);
    model.addAttribute("serverOnly", false);
    model.addAttribute("assetForm", emptyForm());
    addEnums(model);
    addGroups(model);
    addListPage(model, "Assets", "서버와 웹사이트를 모두 관리합니다.", "/assets/new", "Add Asset", "assets");
    return "assets/list";
  }

  @GetMapping("/servers")
  public String servers(Model model) {
    List<Asset> servers = assetService.findAllByType(tenantContext.currentTenantId(), AssetType.SERVER);
    model.addAttribute("assets", servers);
    // OS 계열 통계(윈도우/리눅스/기타 개수) — 카테고리 하위 필터·통계용.
    Map<String, Long> osCounts = new java.util.LinkedHashMap<>();
    for (OsFamily family : OsFamily.values()) {
      osCounts.put(family.name(), servers.stream().filter(s -> s.getOsFamily() == family).count());
    }
    model.addAttribute("osCounts", osCounts);
    model.addAttribute("isServerPage", true);
    model.addAttribute("serverOnly", true);
    model.addAttribute("assetForm", serverForm());
    model.addAttribute("serverProtocols", new AssetProtocol[] {AssetProtocol.SSH, AssetProtocol.RDP});
    addEnums(model);
    addGroups(model);
    addListPage(model, "Servers", "SSH와 RDP 접속 대상 서버를 관리합니다.", "/servers/new", "Add Server", "servers");
    return "assets/servers";
  }

  /** 서버 상세(인프라 관리자): 하드웨어 사양·OS·카테고리·소유팀 등 등록 정보를 보여준다. */
  /**
   * 서버 현황(읽기 전용 집계). {@code /servers}와 한 화면의 탭 둘이라 같은 컨트롤러에 둔다 —
   * 0.7.13 패키지 정리 때 {@code HomeController}에 남아 있었으나 서버 도메인 화면이다.
   */
  @GetMapping("/server-status")
  public String serverStatus(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    List<Asset> servers = assetService.findAllByType(tenantId, AssetType.SERVER);
    long active = servers.stream().filter(s -> s.getStatus() == AssetStatus.ACTIVE).count();
    model.addAttribute("servers", servers);
    model.addAttribute("serverTotal", servers.size());
    model.addAttribute("serverActive", active);
    model.addAttribute("serverInactive", servers.size() - active);
    model.addAttribute("page", "server-status");
    model.addAttribute("pageTitle", "서버 현황");
    model.addAttribute("projectName", "MOA");
    return "server-status";
  }

  @GetMapping("/servers/{id}")
  public String serverDetail(@PathVariable UUID id, Model model) {
    Asset asset = assetService.findById(tenantContext.currentTenantId(), id);
    model.addAttribute("asset", asset);
    addGroups(model);
    model.addAttribute("page", "servers");
    model.addAttribute("pageTitle", "서버 상세");
    model.addAttribute("projectName", "MOA");
    return "assets/detail";
  }

  /** 소유팀 배정/해제(인프라 관리자). 빈 값=해제. 서버 페이지에서 온 경우 그쪽으로 돌아간다. */
  @PostMapping("/assets/{id}/owner")
  public String assignOwner(
      @PathVariable UUID id, @RequestParam(required = false) String ownerGroupId,
      @RequestParam(required = false, defaultValue = "false") boolean fromServers) {
    UUID groupId = parseUuid(ownerGroupId);
    assetService.assignOwnerGroup(tenantContext.currentTenantId(), id, groupId);
    audit("ASSET_SET_OWNER", id, groupId == null ? "해제" : "group=" + groupId);
    return fromServers ? "redirect:/servers" : "redirect:/assets";
  }

  @GetMapping("/assets/new")
  public String createForm(Model model) {
    model.addAttribute("assetForm", emptyForm());
    model.addAttribute("serverOnly", false);
    addEnums(model);
    return "assets/form";
  }

  @GetMapping("/servers/new")
  public String createServerForm(Model model) {
    model.addAttribute("assetForm", serverForm());
    model.addAttribute("serverOnly", true);
    model.addAttribute("serverProtocols", new AssetProtocol[] {AssetProtocol.SSH, AssetProtocol.RDP});
    addEnums(model);
    return "assets/form";
  }

  @PostMapping("/assets")
  public String create(@Valid @ModelAttribute AssetForm assetForm, BindingResult bindingResult, Model model) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("serverOnly", false);
      addEnums(model);
      return "assets/form";
    }
    Asset created = assetService.create(tenantContext.currentTenantId(), assetForm);
    audit("ASSET_CREATE", created.getId(), "생성: " + created.getName());
    return "redirect:/assets";
  }

  @PostMapping("/servers")
  public String createServer(@Valid @ModelAttribute AssetForm assetForm, BindingResult bindingResult, Model model) {
    if (assetForm.protocol() != AssetProtocol.SSH && assetForm.protocol() != AssetProtocol.RDP) {
      bindingResult.rejectValue("protocol", "server.protocol", "서버에는 SSH 또는 RDP 프로토콜만 사용할 수 있습니다.");
    }
    if (assetForm.host() == null || assetForm.host().isBlank()) {
      bindingResult.rejectValue("host", "server.host", "서버 호스트/IP는 필수입니다.");
    }
    if (assetForm.port() == null) {
      bindingResult.rejectValue("port", "server.port", "서버 포트는 필수입니다.");
    }
    if (bindingResult.hasErrors()) {
      model.addAttribute("serverOnly", true);
      model.addAttribute("serverProtocols", new AssetProtocol[] {AssetProtocol.SSH, AssetProtocol.RDP});
      addEnums(model);
      return "assets/form";
    }
    Asset created = assetService.create(tenantContext.currentTenantId(), asServerForm(assetForm));
    audit("ASSET_CREATE", created.getId(), "서버 생성: " + created.getName());
    return "redirect:/servers";
  }

  @GetMapping("/assets/{id}/edit")
  public String editForm(@PathVariable UUID id, Model model) {
    var asset = assetService.findById(tenantContext.currentTenantId(), id);
    model.addAttribute("assetId", id);
    model.addAttribute("serverOnly", false);
    model.addAttribute("assetForm", new AssetForm(
        asset.getName(), asset.getAssetType(), asset.getProtocol(), asset.getHost(), asset.getPort(),
        asset.getUrl(), asset.getOsType(), asset.getCategory(), asset.getOsFamily(),
        asset.getCpu(), asset.getRam(), asset.getDisk(), asset.getHwModel(),
        asset.getDescription(), asset.getStatus()));
    model.addAttribute("ownerGroupId", asset.getOwnerGroupId());
    addEnums(model);
    addGroups(model);
    return "assets/form";
  }

  @PostMapping("/assets/{id}")
  public String update(
      @PathVariable UUID id,
      @Valid @ModelAttribute AssetForm assetForm,
      BindingResult bindingResult,
      Model model) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("assetId", id);
      model.addAttribute("serverOnly", false);
      addEnums(model);
      return "assets/form";
    }
    Asset before = assetService.findById(tenantContext.currentTenantId(), id);
    String diff = diff(before, assetForm);
    boolean wasServer = before.getAssetType() == AssetType.SERVER;
    assetService.update(tenantContext.currentTenantId(), id, assetForm);
    audit("ASSET_UPDATE", id, diff.isEmpty() ? "변경 없음" : diff);
    return wasServer ? "redirect:/servers" : "redirect:/assets";
  }

  @PostMapping("/assets/{id}/delete")
  public String delete(@PathVariable UUID id) {
    Asset before = assetService.findById(tenantContext.currentTenantId(), id);
    String name = before.getName();
    boolean wasServer = before.getAssetType() == AssetType.SERVER;
    assetService.delete(tenantContext.currentTenantId(), id);
    audit("ASSET_DELETE", id, "삭제: " + name);
    return wasServer ? "redirect:/servers" : "redirect:/assets";
  }

  /** 자산 변경 이력(생성·수정·삭제). append-only 감사 로그를 이 자산으로 필터링해 최신순으로 보여준다. */
  @GetMapping("/assets/{id}/history")
  public String history(@PathVariable UUID id, Model model) {
    Asset asset = assetService.findById(tenantContext.currentTenantId(), id);
    UUID tenantId = tenantContext.currentTenantId();
    Map<UUID, String> actorNames = userService.labelsByTenant(tenantId);
    model.addAttribute("asset", asset);
    model.addAttribute("logs", auditLogService.findByTarget(tenantId, TARGET_TYPE, id));
    model.addAttribute("actorNames", actorNames);
    model.addAttribute("currentMenu", "assets");
    model.addAttribute("pageTitle", "변경 이력");
    model.addAttribute("projectName", "MOA");
    return "assets/history";
  }

  private AssetForm emptyForm() {
    return new AssetForm("", AssetType.SERVER, AssetProtocol.SSH, "", 22, "", "LINUX", "", AssetStatus.ACTIVE);
  }

  private AssetForm serverForm() {
    return new AssetForm("", AssetType.SERVER, AssetProtocol.SSH, "", 22, "", "LINUX", "", AssetStatus.ACTIVE);
  }

  private AssetForm asServerForm(AssetForm assetForm) {
    return new AssetForm(
        assetForm.name(), AssetType.SERVER, assetForm.protocol(), assetForm.host(), assetForm.port(), "",
        assetForm.osType(), assetForm.category(), assetForm.osFamily(), assetForm.cpu(), assetForm.ram(),
        assetForm.disk(), assetForm.hwModel(), assetForm.description(), assetForm.status());
  }

  private void addListPage(
      Model model, String title, String description, String createPath, String createLabel, String currentMenu) {
    model.addAttribute("pageTitle", title);
    model.addAttribute("pageDescription", description);
    model.addAttribute("createPath", createPath);
    model.addAttribute("createLabel", createLabel);
    model.addAttribute("currentMenu", currentMenu);
  }

  private void addEnums(Model model) {
    model.addAttribute("assetTypes", AssetType.values());
    model.addAttribute("protocols", AssetProtocol.values());
    model.addAttribute("statuses", AssetStatus.values());
    model.addAttribute("osFamilies", OsFamily.values());
    model.addAttribute("serverCategories",
        categoryService.tree(tenantContext.currentTenantId(), CategoryDomain.SERVER));
  }

  /** 소유팀 선택지 + id→이름 매핑(목록 표시용). */
  private void addGroups(Model model) {
    List<AccessGroup> groups = groupService.findAll(tenantContext.currentTenantId());
    model.addAttribute("groups", groups);
    Map<UUID, String> groupNames = new HashMap<>();
    for (AccessGroup g : groups) {
      groupNames.put(g.getId(), g.getName());
    }
    model.addAttribute("groupNames", groupNames);
  }

  /** 특권 행위 기록. 정책(행위자 없으면 미기록 등)은 TenantAuditRecorder에 있다. */
  private void audit(String action, UUID targetId, String message) {
    auditRecorder.record(TARGET_TYPE, action, targetId, message);
  }

  /** 수정 전/후를 필드 단위로 비교해 사람이 읽을 변경 요약을 만든다(민감정보 없음). 최대 1000자로 자른다. */
  private String diff(Asset before, AssetForm form) {
    List<String> changes = new ArrayList<>();
    addChange(changes, "이름", before.getName(), form.name());
    addChange(changes, "상태", before.getStatus(), form.status());
    addChange(changes, "종류", before.getAssetType(), form.assetType());
    addChange(changes, "프로토콜", before.getProtocol(), form.protocol());
    addChange(changes, "호스트", before.getHost(), form.host());
    addChange(changes, "포트", before.getPort(), form.port());
    addChange(changes, "URL", before.getUrl(), form.url());
    addChange(changes, "OS", before.getOsType(), form.osType());
    addChange(changes, "설명", before.getDescription(), form.description());
    String joined = String.join(", ", changes);
    return joined.length() > 1000 ? joined.substring(0, 1000) : joined;
  }

  private void addChange(List<String> changes, String label, Object before, Object after) {
    String b = before == null ? "" : before.toString();
    String a = after == null ? "" : after.toString();
    if (!Objects.equals(b, a)) {
      changes.add(label + ": '" + b + "'→'" + a + "'");
    }
  }
}
