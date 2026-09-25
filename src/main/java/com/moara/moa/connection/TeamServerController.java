package com.moara.moa.connection;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetForm;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.audit.TenantAuditRecorder;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.security.TenantContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 부서장 위임 — <b>본인이 이끄는 그룹이 소유한 서버(자산)</b>의 추가·편집·삭제를 부서장이 직접 한다.
 * 인프라 관리자 라우트(/servers)를 열지 않고 팀 콘솔 아래 스코프 화면으로 두어 대상을 리더 소유 그룹으로
 * 한정한다. 여기서 만든 서버는 소유팀에 귀속되어 팀원이 자동으로 접근(연결)한다(인프라는 전체 관리).
 */
@Controller
public class TeamServerController {
  private final AssetService assetService;
  private final AccessGroupService groupService;
  private final TenantAuditRecorder auditRecorder;
  private final TenantContext tenantContext;

  public TeamServerController(
      AssetService assetService, AccessGroupService groupService,
      TenantAuditRecorder auditRecorder, TenantContext tenantContext) {
    this.assetService = assetService;
    this.groupService = groupService;
    this.auditRecorder = auditRecorder;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/team/servers")
  public String index(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    List<AccessGroup> ledGroups = groupService.groupsLedBy(tenantId, tenantContext.currentUserId());
    Set<UUID> ledGroupIds = ledGroups.stream().map(AccessGroup::getId).collect(Collectors.toSet());
    Map<UUID, String> groupNames = new HashMap<>();
    ledGroups.forEach(g -> groupNames.put(g.getId(), g.getName()));

    model.addAttribute("servers", assetService.findOwnedByGroups(tenantId, ledGroupIds).stream()
        .filter(a -> a.getAssetType() == AssetType.SERVER).toList());
    model.addAttribute("ledGroups", ledGroups);
    model.addAttribute("groupNames", groupNames);
    model.addAttribute("protocols", new AssetProtocol[] {AssetProtocol.SSH, AssetProtocol.RDP});
    model.addAttribute("page", "team");
    model.addAttribute("pageTitle", "우리 팀 서버 관리");
    model.addAttribute("projectName", "MOA");
    return "team/servers";
  }

  @PostMapping("/team/servers")
  public String create(
      @RequestParam String name, @RequestParam AssetProtocol protocol,
      @RequestParam String host, @RequestParam Integer port,
      @RequestParam(required = false) String osType, @RequestParam UUID ownerGroupId,
      RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!leads(tenantId, ownerGroupId)) {
      redirect.addFlashAttribute("teamError", "본인이 이끄는 부서로만 서버를 등록할 수 있습니다.");
      return "redirect:/team/servers";
    }
    if (protocol != AssetProtocol.SSH && protocol != AssetProtocol.RDP) {
      redirect.addFlashAttribute("teamError", "서버는 SSH 또는 RDP만 가능합니다.");
      return "redirect:/team/servers";
    }
    if (name == null || name.isBlank() || host == null || host.isBlank() || port == null) {
      redirect.addFlashAttribute("teamError", "이름·호스트·포트는 필수입니다.");
      return "redirect:/team/servers";
    }
    Asset created = assetService.create(tenantId, new AssetForm(
        name.trim(), AssetType.SERVER, protocol, host.trim(), port, "",
        osType == null ? "" : osType.trim(), "", AssetStatus.ACTIVE));
    assetService.assignOwnerGroup(tenantId, created.getId(), ownerGroupId);
    audit("TEAM_SERVER_CREATE", created.getId(), created.getName());
    redirect.addFlashAttribute("teamOk", "서버를 등록했습니다: " + created.getName());
    return "redirect:/team/servers";
  }

  @PostMapping("/team/servers/{id}")
  public String update(
      @PathVariable UUID id, @RequestParam String name, @RequestParam AssetProtocol protocol,
      @RequestParam String host, @RequestParam Integer port,
      @RequestParam(required = false) String osType, RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!canManage(tenantId, id)) {
      redirect.addFlashAttribute("teamError", "본인 부서가 소유한 서버만 수정할 수 있습니다.");
      return "redirect:/team/servers";
    }
    Asset current = assetService.findById(tenantId, id);
    assetService.update(tenantId, id, new AssetForm(
        name.trim(), AssetType.SERVER, protocol, host.trim(), port, current.getUrl(),
        osType == null ? "" : osType.trim(), current.getDescription(), current.getStatus()));
    audit("TEAM_SERVER_UPDATE", id, name);
    redirect.addFlashAttribute("teamOk", "수정했습니다: " + name);
    return "redirect:/team/servers";
  }

  @PostMapping("/team/servers/{id}/delete")
  public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!canManage(tenantId, id)) {
      redirect.addFlashAttribute("teamError", "본인 부서가 소유한 서버만 삭제할 수 있습니다.");
      return "redirect:/team/servers";
    }
    assetService.delete(tenantId, id);
    audit("TEAM_SERVER_DELETE", id, null);
    redirect.addFlashAttribute("teamOk", "삭제했습니다.");
    return "redirect:/team/servers";
  }

  /** 대상 서버가 '내가 이끄는 그룹' 소유인지. */
  private boolean canManage(UUID tenantId, UUID assetId) {
    UUID owner;
    try {
      owner = assetService.findById(tenantId, assetId).getOwnerGroupId();
    } catch (RuntimeException notFound) {
      return false;
    }
    return owner != null && leads(tenantId, owner);
  }

  private boolean leads(UUID tenantId, UUID groupId) {
    return groupService.groupsLedBy(tenantId, tenantContext.currentUserId()).stream()
        .anyMatch(g -> g.getId().equals(groupId));
  }

  /** 특권 행위 기록. 정책(행위자 없으면 미기록 등)은 TenantAuditRecorder에 있다. */
  private void audit(String action, UUID targetId, String message) {
    auditRecorder.record("Asset", action, targetId, message);
  }
}
