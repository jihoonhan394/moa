package com.moara.moa.web;

import com.moara.moa.asset.AssetService;
import com.moara.moa.maintenance.MaintenanceService;
import com.moara.moa.maintenance.MaintenanceTargetType;
import com.moara.moa.maintenance.MaintenanceWindowForm;
import com.moara.moa.security.TenantContext;
import com.moara.moa.solution.ManagedSolutionService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import jakarta.validation.Valid;
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

/**
 * 점검/작업 일정과 유지보수 담당자 관리(인프라 관리자 전용). 점검창 등록 시 관련자(담당자 + 솔루션
 * 배정자)에게 인앱 알림·메일이 나간다. 라우팅 권한은 SecurityConfig(/maintenance/** → INFRA_MANAGER)에서 강제.
 */
@Controller
public class MaintenanceController {
  private final MaintenanceService maintenanceService;
  private final AssetService assetService;
  private final ManagedSolutionService solutionService;
  private final ManagedUserService userService;
  private final TenantContext tenantContext;

  public MaintenanceController(
      MaintenanceService maintenanceService, AssetService assetService,
      ManagedSolutionService solutionService, ManagedUserService userService,
      TenantContext tenantContext) {
    this.maintenanceService = maintenanceService;
    this.assetService = assetService;
    this.solutionService = solutionService;
    this.userService = userService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/maintenance")
  public String index(Model model) {
    populate(model, new MaintenanceWindowForm(null, null, null, null));
    return "maintenance";
  }

  @PostMapping("/maintenance/owners")
  public String addOwner(@RequestParam String target, @RequestParam UUID userId) {
    Target parsed = Target.parse(target);
    if (parsed != null) {
      UUID tenantId = tenantContext.currentTenantId();
      requireOwnTarget(tenantId, parsed);
      userService.findById(tenantId, userId); // 담당자도 자기 기관 사용자여야 한다
      maintenanceService.addOwner(tenantId, parsed.type(), parsed.id(), userId);
    }
    return "redirect:/maintenance";
  }

  @PostMapping("/maintenance/owners/{id}/delete")
  public String removeOwner(@PathVariable UUID id) {
    maintenanceService.removeOwner(tenantContext.currentTenantId(), id);
    return "redirect:/maintenance";
  }

  @PostMapping("/maintenance/windows")
  public String createWindow(
      @RequestParam String target,
      @Valid @ModelAttribute("windowForm") MaintenanceWindowForm windowForm,
      BindingResult binding,
      Model model) {
    Target parsed = Target.parse(target);
    if (parsed == null) {
      binding.reject("target", "대상을 선택하세요.");
    }
    if (binding.hasErrors()) {
      populate(model, windowForm);
      return "maintenance";
    }
    UUID tenantId = tenantContext.currentTenantId();
    requireOwnTarget(tenantId, parsed);
    maintenanceService.createWindow(
        tenantId, parsed.type(), parsed.id(), tenantContext.currentUserId(), windowForm);
    return "redirect:/maintenance";
  }

  /**
   * 대상(자산/솔루션)이 현재 기관 소속인지 검증한다. 누락하면 타 기관 자원에 점검 일정·담당자를
   * 붙일 수 있고, 알림이 타 기관 사용자에게 발송된다(AGENTS.md 멀티테넌트 불변식).
   */
  private void requireOwnTarget(UUID tenantId, Target target) {
    switch (target.type()) {
      case ASSET -> assetService.findById(tenantId, target.id());
      case SOLUTION -> solutionService.findById(tenantId, target.id());
    }
  }

  /** 화면의 단일 대상 선택값("TYPE:uuid")을 타입+식별자로 분해한다. 형식이 틀리면 null. */
  private record Target(MaintenanceTargetType type, UUID id) {
    static Target parse(String value) {
      if (value == null) {
        return null;
      }
      int sep = value.indexOf(':');
      if (sep <= 0 || sep == value.length() - 1) {
        return null;
      }
      try {
        return new Target(
            MaintenanceTargetType.valueOf(value.substring(0, sep)),
            UUID.fromString(value.substring(sep + 1)));
      } catch (IllegalArgumentException exception) {
        return null;
      }
    }
  }

  /** 목록·이름표·폼을 화면 모델에 채운다(GET과 검증 실패 재표시 공용). */
  private void populate(Model model, MaintenanceWindowForm windowForm) {
    UUID tenantId = tenantContext.currentTenantId();
    model.addAttribute("assets", assetService.findAll(tenantId));
    model.addAttribute("solutions", solutionService.findAll(tenantId));
    model.addAttribute("assignableUsers", assignableUsers(tenantId));
    model.addAttribute("owners", maintenanceService.owners(tenantId));
    model.addAttribute("windows", maintenanceService.windows(tenantId));
    model.addAttribute("targetTypes", MaintenanceTargetType.values());
    model.addAttribute("targetNames", targetNames(tenantId));
    model.addAttribute("userNames", userNames(tenantId));
    if (!model.containsAttribute("windowForm")) {
      model.addAttribute("windowForm", windowForm);
    }
    model.addAttribute("page", "maintenance");
  }

  /** 담당자 지정 가능 사용자(활성 계정). 관리자/일반 구분 없이 담당은 누구나 될 수 있다. */
  private List<ManagedUser> assignableUsers(UUID tenantId) {
    return userService.findByTenant(tenantId).stream()
        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
        .filter(user -> !user.hasRole(UserRole.SYSTEM_ADMIN))
        .toList();
  }

  /** "TYPE:id" → 대상 이름(목록 표시용). */
  private Map<String, String> targetNames(UUID tenantId) {
    Map<String, String> names = new LinkedHashMap<>();
    assetService.findAll(tenantId).forEach(
        a -> names.put(MaintenanceTargetType.ASSET + ":" + a.getId(), a.getName()));
    solutionService.findAll(tenantId).forEach(
        s -> names.put(MaintenanceTargetType.SOLUTION + ":" + s.getId(), s.getName()));
    return names;
  }

  /** userId → 이름(목록 표시용). */
  private Map<UUID, String> userNames(UUID tenantId) {
    Map<UUID, String> names = new LinkedHashMap<>();
    userService.findByTenant(tenantId).forEach(u -> names.put(u.getId(), u.getName()));
    return names;
  }
}
