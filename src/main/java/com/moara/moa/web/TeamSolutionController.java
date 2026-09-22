package com.moara.moa.web;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetType;
import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.credential.CredentialService;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.remote.RemoteProtocol;
import com.moara.moa.security.TenantContext;
import com.moara.moa.solution.ControlAction;
import com.moara.moa.solution.ControlResult;
import com.moara.moa.solution.DuplicateSolutionException;
import com.moara.moa.solution.HealthCheckType;
import com.moara.moa.solution.ManagedSolution;
import com.moara.moa.solution.ManagedSolutionService;
import com.moara.moa.solution.SolutionControlService;
import com.moara.moa.solution.SolutionForm;
import com.moara.moa.solution.SolutionStatus;
import com.moara.moa.solution.SolutionType;
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
 * 부서장 위임 — <b>본인이 이끄는 그룹이 소유한 솔루션</b>의 추가·편집·삭제·제어를 부서장이 직접 한다.
 * 봉쇄(containment): 솔루션이 붙는 <b>서버는 반드시 팀 소유 서버</b>여야 한다 → 제어 명령의 영향 범위가
 * 팀이 이미 소유한 서버로 제한된다. 인프라 관리자는 /solutions에서 전체 관리(오버사이트).
 */
@Controller
public class TeamSolutionController {
  private final ManagedSolutionService solutionService;
  private final SolutionControlService controlService;
  private final AssetService assetService;
  private final CredentialService credentialService;
  private final AccessGroupService groupService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public TeamSolutionController(
      ManagedSolutionService solutionService, SolutionControlService controlService,
      AssetService assetService, CredentialService credentialService, AccessGroupService groupService,
      AuditLogService auditLogService, TenantContext tenantContext) {
    this.solutionService = solutionService;
    this.controlService = controlService;
    this.assetService = assetService;
    this.credentialService = credentialService;
    this.groupService = groupService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/team/solutions")
  public String index(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    Set<UUID> ledGroupIds = ledGroupIds(tenantId);
    List<AccessGroup> ledGroups = groupService.groupsLedBy(tenantId, tenantContext.currentUserId());

    List<ManagedSolution> solutions = solutionService.findAll(tenantId).stream()
        .filter(s -> s.getOwnerGroupId() != null && ledGroupIds.contains(s.getOwnerGroupId())).toList();
    // 봉쇄: 붙일 수 있는 서버는 팀 소유 서버뿐.
    List<Asset> teamServers = assetService.findOwnedByGroups(tenantId, ledGroupIds).stream()
        .filter(a -> a.getAssetType() == AssetType.SERVER).toList();
    Map<UUID, String> assetNames = new HashMap<>();
    assetService.findAll(tenantId).forEach(a -> assetNames.put(a.getId(), a.getName()));
    Map<UUID, String> groupNames = new HashMap<>();
    ledGroups.forEach(g -> groupNames.put(g.getId(), g.getName()));

    model.addAttribute("solutions", solutions);
    model.addAttribute("teamServers", teamServers);
    model.addAttribute("assetNames", assetNames);
    model.addAttribute("credentials", credentialService.findAll(tenantId));
    model.addAttribute("ledGroups", ledGroups);
    model.addAttribute("groupNames", groupNames);
    model.addAttribute("types", SolutionType.values());
    model.addAttribute("healthTypes", HealthCheckType.values());
    model.addAttribute("statuses", SolutionStatus.values());
    model.addAttribute("protocols", RemoteProtocol.values());
    model.addAttribute("actions", ControlAction.values());
    model.addAttribute("page", "team");
    model.addAttribute("pageTitle", "우리 팀 솔루션 관리");
    model.addAttribute("projectName", "MOA");
    return "team/solutions";
  }

  @PostMapping("/team/solutions")
  public String create(
      @RequestParam UUID assetId, @RequestParam String name, @RequestParam SolutionType type,
      @RequestParam String identifier, @RequestParam(required = false) String credentialId,
      @RequestParam HealthCheckType healthCheckType, @RequestParam(required = false) String healthCheckTarget,
      @RequestParam SolutionStatus status, @RequestParam(required = false) String startCommand,
      @RequestParam(required = false) String stopCommand, @RequestParam(required = false) String statusCommand,
      @RequestParam(defaultValue = "SSH") RemoteProtocol controlProtocol,
      @RequestParam(required = false) Integer controlPort, @RequestParam UUID ownerGroupId,
      RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!leads(tenantId, ownerGroupId)) {
      redirect.addFlashAttribute("teamError", "본인이 이끄는 부서로만 등록할 수 있습니다.");
      return "redirect:/team/solutions";
    }
    if (!isTeamServer(tenantId, assetId)) {
      redirect.addFlashAttribute("teamError", "솔루션은 우리 팀이 소유한 서버에만 등록할 수 있습니다.");
      return "redirect:/team/solutions";
    }
    try {
      SolutionForm form = new SolutionForm(assetId, name, type, identifier, parseUuid(credentialId),
          healthCheckType, healthCheckTarget, status, startCommand, stopCommand, statusCommand,
          controlProtocol, controlPort, ownerGroupId);
      ManagedSolution created = solutionService.create(tenantId, form);
      audit("TEAM_SOLUTION_CREATE", created.getId(), created.getName());
      redirect.addFlashAttribute("teamOk", "솔루션을 등록했습니다: " + created.getName());
    } catch (DuplicateSolutionException e) {
      redirect.addFlashAttribute("teamError", "이미 사용 중인 솔루션 이름입니다.");
    }
    return "redirect:/team/solutions";
  }

  @PostMapping("/team/solutions/{id}")
  public String update(
      @PathVariable UUID id, @RequestParam UUID assetId, @RequestParam String name,
      @RequestParam SolutionType type, @RequestParam String identifier,
      @RequestParam(required = false) String credentialId, @RequestParam HealthCheckType healthCheckType,
      @RequestParam(required = false) String healthCheckTarget, @RequestParam SolutionStatus status,
      @RequestParam(required = false) String startCommand, @RequestParam(required = false) String stopCommand,
      @RequestParam(required = false) String statusCommand,
      @RequestParam(defaultValue = "SSH") RemoteProtocol controlProtocol,
      @RequestParam(required = false) Integer controlPort, RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!canManage(tenantId, id)) {
      redirect.addFlashAttribute("teamError", "본인 부서가 소유한 솔루션만 수정할 수 있습니다.");
      return "redirect:/team/solutions";
    }
    if (!isTeamServer(tenantId, assetId)) {
      redirect.addFlashAttribute("teamError", "우리 팀이 소유한 서버에만 연결할 수 있습니다.");
      return "redirect:/team/solutions";
    }
    UUID ownerGroupId = solutionService.findById(tenantId, id).getOwnerGroupId(); // 소유팀 유지
    try {
      solutionService.update(tenantId, id, new SolutionForm(assetId, name, type, identifier,
          parseUuid(credentialId), healthCheckType, healthCheckTarget, status, startCommand, stopCommand,
          statusCommand, controlProtocol, controlPort, ownerGroupId));
      audit("TEAM_SOLUTION_UPDATE", id, name);
      redirect.addFlashAttribute("teamOk", "수정했습니다: " + name);
    } catch (DuplicateSolutionException e) {
      redirect.addFlashAttribute("teamError", "이미 사용 중인 솔루션 이름입니다.");
    }
    return "redirect:/team/solutions";
  }

  @PostMapping("/team/solutions/{id}/delete")
  public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!canManage(tenantId, id)) {
      redirect.addFlashAttribute("teamError", "본인 부서가 소유한 솔루션만 삭제할 수 있습니다.");
      return "redirect:/team/solutions";
    }
    solutionService.delete(tenantId, id);
    audit("TEAM_SOLUTION_DELETE", id, null);
    redirect.addFlashAttribute("teamOk", "삭제했습니다.");
    return "redirect:/team/solutions";
  }

  @PostMapping("/team/solutions/{id}/control")
  public String control(
      @PathVariable UUID id, @RequestParam ControlAction action, RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!canManage(tenantId, id)) {
      redirect.addFlashAttribute("teamError", "본인 부서가 소유한 솔루션만 제어할 수 있습니다.");
      return "redirect:/team/solutions";
    }
    try {
      ControlResult result = controlService.control(tenantId, id, action);
      audit("TEAM_SOLUTION_CONTROL", id, action + " success=" + result.success());
      redirect.addFlashAttribute("teamOk", action + " → " + (result.success() ? "성공" : "실패"));
    } catch (RuntimeException e) {
      audit("TEAM_SOLUTION_CONTROL", id, action + " error");
      redirect.addFlashAttribute("teamError", action + " 실패: " + e.getMessage());
    }
    return "redirect:/team/solutions";
  }

  private boolean canManage(UUID tenantId, UUID solutionId) {
    UUID owner;
    try {
      owner = solutionService.findById(tenantId, solutionId).getOwnerGroupId();
    } catch (RuntimeException notFound) {
      return false;
    }
    return owner != null && leads(tenantId, owner);
  }

  /** 붙일 서버가 팀 소유 서버인지(봉쇄). */
  private boolean isTeamServer(UUID tenantId, UUID assetId) {
    UUID owner;
    try {
      owner = assetService.findById(tenantId, assetId).getOwnerGroupId();
    } catch (RuntimeException notFound) {
      return false;
    }
    return owner != null && leads(tenantId, owner);
  }

  private Set<UUID> ledGroupIds(UUID tenantId) {
    return groupService.groupsLedBy(tenantId, tenantContext.currentUserId()).stream()
        .map(AccessGroup::getId).collect(Collectors.toSet());
  }

  private boolean leads(UUID tenantId, UUID groupId) {
    return ledGroupIds(tenantId).contains(groupId);
  }

  private static UUID parseUuid(String value) {
    return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
  }

  private void audit(String action, UUID targetId, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "ManagedSolution", targetId,
          AuditResult.SUCCESS, message);
    }
  }
}
