package com.moara.moa.solution;

import com.moara.moa.asset.AssetService;
import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.credential.CredentialService;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.maintenance.MaintenanceService;
import com.moara.moa.maintenance.MaintenanceTargetType;
import com.moara.moa.remote.RemoteProtocol;
import com.moara.moa.security.TenantContext;
import com.moara.moa.wiki.WikiSpace;
import com.moara.moa.wiki.WikiSpaceService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import java.util.HashMap;
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
 * 솔루션 등록/제어 UI(관리자 전용). 등록된 솔루션을 start/stop/restart/status로 제어하며,
 * 제어는 {@link SolutionControlService}(볼트 자격증명 + 원격 실행)를 통하고 모든 행위를 감사한다.
 */
@Controller
public class SolutionController {
  private final ManagedSolutionService solutionService;
  private final SolutionControlService controlService;
  private final SolutionAccessService accessService;
  private final AssetService assetService;
  private final CredentialService credentialService;
  private final ManagedUserService userService;
  private final AuditLogService auditLogService;
  private final MaintenanceService maintenanceService;
  private final WikiSpaceService wikiSpaceService;
  private final LogAnalysisService logAnalysisService;
  private final AccessGroupService groupService;
  private final com.moara.moa.solution.SolutionSequenceService sequenceService;
  private final com.moara.moa.category.CategoryService categoryService;
  private final TenantContext tenantContext;

  public SolutionController(
      ManagedSolutionService solutionService, SolutionControlService controlService,
      SolutionAccessService accessService, AssetService assetService, CredentialService credentialService,
      ManagedUserService userService, AuditLogService auditLogService,
      MaintenanceService maintenanceService, WikiSpaceService wikiSpaceService,
      LogAnalysisService logAnalysisService, AccessGroupService groupService,
      com.moara.moa.solution.SolutionSequenceService sequenceService,
      com.moara.moa.category.CategoryService categoryService, TenantContext tenantContext) {
    this.solutionService = solutionService;
    this.controlService = controlService;
    this.accessService = accessService;
    this.assetService = assetService;
    this.credentialService = credentialService;
    this.userService = userService;
    this.auditLogService = auditLogService;
    this.maintenanceService = maintenanceService;
    this.wikiSpaceService = wikiSpaceService;
    this.logAnalysisService = logAnalysisService;
    this.groupService = groupService;
    this.sequenceService = sequenceService;
    this.categoryService = categoryService;
    this.tenantContext = tenantContext;
  }

  /** 솔루션 운영 콘솔(상세): 제어 요약 + 위키 매뉴얼 + 유지보수 업체·담당자 + 점검·작업 이력 + 로그 분석. */
  @GetMapping("/solutions/{id}")
  public String detail(@PathVariable UUID id, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    populateDetail(model, tenantId, solutionService.findById(tenantId, id));
    return "solutions/detail";
  }

  /** 로그 온디맨드 분석(비용 가드: 오류 0건이면 AI 미호출). 결과를 상세 화면에 함께 렌더한다. */
  @PostMapping("/solutions/{id}/log-analyze")
  public String logAnalyze(@PathVariable UUID id, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    ManagedSolution solution = solutionService.findById(tenantId, id);
    try {
      model.addAttribute("logAnalysis", logAnalysisService.analyze(tenantId, id));
      audit("SOLUTION_LOG_ANALYZE", id, null);
    } catch (RuntimeException exception) {
      model.addAttribute("logError", "로그 분석 실패: " + exception.getMessage());
    }
    populateDetail(model, tenantId, solution);
    return "solutions/detail";
  }

  private void populateDetail(Model model, UUID tenantId, ManagedSolution solution) {
    UUID id = solution.getId();
    Map<UUID, String> userNames = new HashMap<>();
    for (ManagedUser u : userService.findByTenant(tenantId)) {
      userNames.put(u.getId(), u.getName());
    }
    WikiSpace manualSpace = solution.getWikiSpaceId() == null ? null
        : wikiSpaceService.findAll(tenantId).stream()
            .filter(s -> s.getId().equals(solution.getWikiSpaceId())).findFirst().orElse(null);
    model.addAttribute("solution", solution);
    model.addAttribute("manualSpace", manualSpace);
    model.addAttribute("spaces", wikiSpaceService.findAll(tenantId));
    model.addAttribute("owners", maintenanceService.ownersFor(tenantId, MaintenanceTargetType.SOLUTION, id));
    model.addAttribute("history", maintenanceService.windowsFor(tenantId, MaintenanceTargetType.SOLUTION, id));
    model.addAttribute("userNames", userNames);
    model.addAttribute("actions", ControlAction.values());
    model.addAttribute("opsForm", new SolutionOpsForm(
        solution.getWikiSpaceId(), solution.getVendorName(), solution.getVendorContact(),
        solution.getVendorNote(), solution.getLogCommand()));
    model.addAttribute("page", "solutions");
  }

  /** 운영 정보 갱신(위키 매뉴얼·유지보수 업체). 위키 공간은 같은 기관 소유만 허용. */
  @PostMapping("/solutions/{id}/ops")
  public String updateOps(
      @PathVariable UUID id, @jakarta.validation.Valid SolutionOpsForm opsForm,
      RedirectAttributes redirectAttributes) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID requested = opsForm.wikiSpaceId();
    // 타 기관/삭제된 공간은 무시(격리) — 같은 기관 소유일 때만 연결.
    UUID spaceId = (requested != null
        && wikiSpaceService.findAll(tenantId).stream().noneMatch(s -> s.getId().equals(requested)))
        ? null : requested;
    solutionService.updateOps(tenantId, id, new SolutionOpsForm(
        spaceId, opsForm.vendorName(), opsForm.vendorContact(), opsForm.vendorNote(), opsForm.logCommand()));
    audit("SOLUTION_UPDATE_OPS", id, null);
    redirectAttributes.addFlashAttribute("controlMessage", "운영 정보를 저장했습니다.");
    return "redirect:/solutions/" + id;
  }

  @GetMapping("/solutions")
  public String list(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    var solutions = solutionService.findAll(tenantId);
    // 솔루션별 배정 사용자(제어 권한 부여 대상).
    Map<UUID, ManagedUser> userById = new HashMap<>();
    for (ManagedUser u : userService.findByTenant(tenantId)) {
      userById.put(u.getId(), u);
    }
    Map<UUID, java.util.List<ManagedUser>> solutionUsers = new HashMap<>();
    for (ManagedSolution s : solutions) {
      solutionUsers.put(s.getId(), accessService.assignedUserIds(tenantId, s.getId()).stream()
          .map(userById::get).filter(java.util.Objects::nonNull).toList());
    }
    // 소유팀(그룹) 선택지 + id→이름 매핑(목록 표시용).
    java.util.List<AccessGroup> groups = groupService.findAll(tenantId);
    Map<UUID, String> groupNames = new HashMap<>();
    for (AccessGroup g : groups) {
      groupNames.put(g.getId(), g.getName());
    }
    model.addAttribute("solutions", solutions);
    model.addAttribute("groups", groups);
    model.addAttribute("groupNames", groupNames);
    model.addAttribute("tenantUsers", userService.findByTenant(tenantId));
    model.addAttribute("solutionUsers", solutionUsers);
    model.addAttribute("assets", assetService.findAll(tenantId));
    model.addAttribute("credentials", credentialService.findAll(tenantId));
    model.addAttribute("types", SolutionType.values());
    model.addAttribute("healthTypes", HealthCheckType.values());
    model.addAttribute("statuses", SolutionStatus.values());
    model.addAttribute("protocols", RemoteProtocol.values());
    model.addAttribute("actions", ControlAction.values());
    model.addAttribute("solutionCategories",
        categoryService.tree(tenantId, com.moara.moa.category.CategoryDomain.SOLUTION));
    // 기동 순서(같은 화면 탭). 스텝 뷰까지 구성해 시퀀스 탭에서 바로 렌더.
    addSequenceModel(model, tenantId, solutions);
    model.addAttribute("page", "solutions");
    model.addAttribute("pageTitle", "솔루션 · 기동 순서");
    model.addAttribute("projectName", "MOA");
    return "solutions/list";
  }

  /** 기동 순서 탭 데이터: 순서 목록 + 스텝 뷰(순번·솔루션명·유형·동작·대기·확인) + 등록 폼. */
  private void addSequenceModel(Model model, UUID tenantId, java.util.List<ManagedSolution> solutions) {
    if (!model.containsAttribute("sequenceForm")) {
      model.addAttribute("sequenceForm", new com.moara.moa.solution.SolutionSequenceForm(null, null));
    }
    Map<UUID, ManagedSolution> solutionById = new HashMap<>();
    for (ManagedSolution s : solutions) {
      solutionById.put(s.getId(), s);
    }
    var sequences = sequenceService.findAll(tenantId);
    Map<UUID, java.util.List<com.moara.moa.solution.SolutionSequenceStepView>> stepsBySequence =
        new HashMap<>();
    for (var seq : sequences) {
      java.util.List<com.moara.moa.solution.SolutionSequenceStepView> views = new java.util.ArrayList<>();
      int order = 1;
      for (var step : sequenceService.steps(tenantId, seq.getId())) {
        ManagedSolution sol = solutionById.get(step.getSolutionId());
        views.add(new com.moara.moa.solution.SolutionSequenceStepView(step.getId(), order++,
            sol != null ? sol.getName() : "(삭제됨)", sol != null ? sol.getType().name() : "-",
            step.getAction(), step.getWaitSeconds(), step.isVerifyAfterStart()));
      }
      stepsBySequence.put(seq.getId(), views);
    }
    model.addAttribute("sequences", sequences);
    model.addAttribute("stepsBySequence", stepsBySequence);
  }

  @PostMapping("/solutions")
  public String create(
      @RequestParam UUID assetId,
      @RequestParam String name,
      @RequestParam SolutionType type,
      @RequestParam String identifier,
      @RequestParam(required = false) String credentialId,
      @RequestParam HealthCheckType healthCheckType,
      @RequestParam(required = false) String healthCheckTarget,
      @RequestParam SolutionStatus status,
      @RequestParam(required = false) String startCommand,
      @RequestParam(required = false) String stopCommand,
      @RequestParam(required = false) String statusCommand,
      @RequestParam(defaultValue = "SSH") RemoteProtocol controlProtocol,
      @RequestParam(required = false) Integer controlPort,
      @RequestParam(required = false) String ownerGroupId,
      @RequestParam(required = false) String category) {
    SolutionForm form = new SolutionForm(assetId, name, type, identifier, parseUuid(credentialId),
        healthCheckType, healthCheckTarget, status, startCommand, stopCommand, statusCommand,
        controlProtocol, controlPort, parseUuid(ownerGroupId), category);
    ManagedSolution created = solutionService.create(tenantContext.currentTenantId(), form);
    audit("SOLUTION_CREATE", created.getId(), created.getName());
    return "redirect:/solutions";
  }

  /** 솔루션 수정 화면(등록값 전체 편집 — 자격증명·명령·제어채널 등). */
  @GetMapping("/solutions/{id}/edit")
  public String editForm(@PathVariable UUID id, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    model.addAttribute("solution", solutionService.findById(tenantId, id));
    addSolutionFormRefData(model, tenantId);
    model.addAttribute("page", "solutions");
    model.addAttribute("pageTitle", "솔루션 수정");
    model.addAttribute("projectName", "MOA");
    return "solutions/edit";
  }

  @PostMapping("/solutions/{id}/update")
  public String update(
      @PathVariable UUID id,
      @RequestParam UUID assetId, @RequestParam String name, @RequestParam SolutionType type,
      @RequestParam String identifier, @RequestParam(required = false) String credentialId,
      @RequestParam HealthCheckType healthCheckType,
      @RequestParam(required = false) String healthCheckTarget, @RequestParam SolutionStatus status,
      @RequestParam(required = false) String startCommand, @RequestParam(required = false) String stopCommand,
      @RequestParam(required = false) String statusCommand,
      @RequestParam(defaultValue = "SSH") RemoteProtocol controlProtocol,
      @RequestParam(required = false) Integer controlPort, @RequestParam(required = false) String ownerGroupId,
      @RequestParam(required = false) String category) {
    SolutionForm form = new SolutionForm(assetId, name, type, identifier, parseUuid(credentialId),
        healthCheckType, healthCheckTarget, status, startCommand, stopCommand, statusCommand,
        controlProtocol, controlPort, parseUuid(ownerGroupId), category);
    solutionService.update(tenantContext.currentTenantId(), id, form);
    audit("SOLUTION_UPDATE", id, name);
    return "redirect:/solutions";
  }

  private void addSolutionFormRefData(Model model, UUID tenantId) {
    model.addAttribute("assets", assetService.findAll(tenantId));
    model.addAttribute("credentials", credentialService.findAll(tenantId));
    model.addAttribute("types", SolutionType.values());
    model.addAttribute("healthTypes", HealthCheckType.values());
    model.addAttribute("statuses", SolutionStatus.values());
    model.addAttribute("protocols", RemoteProtocol.values());
    model.addAttribute("groups", groupService.findAll(tenantId));
    model.addAttribute("solutionCategories",
        categoryService.tree(tenantId, com.moara.moa.category.CategoryDomain.SOLUTION));
  }

  /** 소유팀 배정/해제(인프라 관리자). 빈 값=해제(인프라 전용으로 되돌림). */
  @PostMapping("/solutions/{id}/owner")
  public String assignOwner(@PathVariable UUID id, @RequestParam(required = false) String ownerGroupId) {
    UUID groupId = parseUuid(ownerGroupId);
    solutionService.assignOwnerGroup(tenantContext.currentTenantId(), id, groupId);
    audit("SOLUTION_SET_OWNER", id, groupId == null ? "해제" : "group=" + groupId);
    return "redirect:/solutions";
  }

  @PostMapping("/solutions/{id}/delete")
  public String delete(@PathVariable UUID id) {
    solutionService.delete(tenantContext.currentTenantId(), id);
    audit("SOLUTION_DELETE", id, null);
    return "redirect:/solutions";
  }

  @PostMapping("/solutions/{id}/users")
  public String assignUser(@PathVariable UUID id, @RequestParam UUID userId) {
    accessService.assignUser(tenantContext.currentTenantId(), id, userId);
    audit("SOLUTION_ASSIGN_USER", id, "user=" + userId);
    return "redirect:/solutions";
  }

  @PostMapping("/solutions/{id}/users/delete")
  public String unassignUser(@PathVariable UUID id, @RequestParam UUID userId) {
    accessService.unassignUser(tenantContext.currentTenantId(), id, userId);
    audit("SOLUTION_UNASSIGN_USER", id, "user=" + userId);
    return "redirect:/solutions";
  }

  @PostMapping("/solutions/{id}/control")
  public String control(
      @PathVariable UUID id, @RequestParam ControlAction action, RedirectAttributes redirectAttributes) {
    try {
      ControlResult result = controlService.control(tenantContext.currentTenantId(), id, action);
      audit("SOLUTION_CONTROL", id, action + " success=" + result.success());
      redirectAttributes.addFlashAttribute("controlMessage",
          action + " → " + (result.success() ? "성공" : "실패") + " : " + result.output());
    } catch (RuntimeException exception) {
      audit("SOLUTION_CONTROL", id, action + " error");
      redirectAttributes.addFlashAttribute("controlError", action + " 실패: " + exception.getMessage());
    }
    return "redirect:/solutions";
  }

  private UUID parseUuid(String value) {
    return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
  }

  private void audit(String action, UUID targetId, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "ManagedSolution", targetId, AuditResult.SUCCESS, message);
    }
  }
}
