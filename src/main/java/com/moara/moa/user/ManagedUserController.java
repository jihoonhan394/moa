package com.moara.moa.user;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.security.TenantContext;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

@Controller
public class ManagedUserController {
  private final ManagedUserService userService;
  private final UserLifecycleService lifecycleService;
  private final AuditLogService auditLogService;
  private final com.moara.moa.tenant.TenantService tenantService;
  private final TenantContext tenantContext;

  public ManagedUserController(
      ManagedUserService userService, UserLifecycleService lifecycleService,
      AuditLogService auditLogService, com.moara.moa.tenant.TenantService tenantService,
      TenantContext tenantContext) {
    this.userService = userService;
    this.lifecycleService = lifecycleService;
    this.auditLogService = auditLogService;
    this.tenantService = tenantService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/users")
  public String list(Model model) {
    model.addAttribute("users", userService.findByTenant(tenantContext.currentTenantId()));
    return "users/list";
  }

  @GetMapping("/users/new")
  public String createForm(Model model) {
    model.addAttribute("userForm", new UserForm("", "", "", "", UserStatus.ACTIVE));
    addFormModel(model, Set.of());
    return "users/form";
  }

  @PostMapping("/users")
  public String create(
      @Valid @ModelAttribute UserForm userForm,
      BindingResult bindingResult,
      @RequestParam(required = false) String passwordConfirm,
      @RequestParam(name = "roles", required = false) List<UserRole> roles,
      Model model) {
    Set<UserRole> roleSet = toRoleSet(roles);
    if (userForm.password() == null || userForm.password().length() < 8) {
      bindingResult.rejectValue("password", "password.length", "비밀번호는 8자 이상이어야 합니다.");
    }
    if (!passwordMatches(userForm.password(), passwordConfirm)) {
      bindingResult.rejectValue("password", "password.mismatch", "비밀번호가 일치하지 않습니다.");
    }
    if (bindingResult.hasErrors()) {
      addFormModel(model, roleSet);
      return "users/form";
    }
    try {
      ManagedUser created = userService.create(tenantContext.currentTenantId(), userForm, roleSet);
      audit("USER_CREATE", created.getId(), created.getUsername());
      return "redirect:/users";
    } catch (DuplicateManagedUserException exception) {
      bindingResult.reject("user.duplicate", "이미 사용 중인 로그인 ID 또는 이메일입니다.");
      addFormModel(model, roleSet);
      return "users/form";
    }
  }

  @GetMapping("/users/{id}/edit")
  public String editForm(@PathVariable UUID id, Model model) {
    ManagedUser user = userService.findById(tenantContext.currentTenantId(), id);
    model.addAttribute("userId", id);
    model.addAttribute("userForm", new UserForm(
        user.getUsername(), user.getName(), user.getEmail(), user.getPhoneDisplay(), "", user.getStatus()));
    addFormModel(model, user.getRoles());
    return "users/form";
  }

  @PostMapping("/users/{id}")
  public String update(
      @PathVariable UUID id,
      @Valid @ModelAttribute UserForm userForm,
      BindingResult bindingResult,
      @RequestParam(required = false) String passwordConfirm,
      @RequestParam(name = "roles", required = false) List<UserRole> roles,
      Model model) {
    Set<UserRole> roleSet = toRoleSet(roles);
    if (!passwordMatches(userForm.password(), passwordConfirm)) {
      bindingResult.rejectValue("password", "password.mismatch", "비밀번호가 일치하지 않습니다.");
    }
    if (bindingResult.hasErrors()) {
      model.addAttribute("userId", id);
      addFormModel(model, roleSet);
      return "users/form";
    }
    try {
      UUID tenantId = tenantContext.currentTenantId();
      userService.update(tenantId, id, userForm);
      userService.assignRoles(tenantId, id, roleSet);
      audit("USER_UPDATE", id, userForm.username());
      return "redirect:/users";
    } catch (DuplicateManagedUserException exception) {
      bindingResult.reject("user.duplicate", "이미 사용 중인 로그인 ID 또는 이메일입니다.");
      model.addAttribute("userId", id);
      addFormModel(model, roleSet);
      return "users/form";
    }
  }

  /** 체크된 역할이 없으면 일반 사용자({USER}). */
  private Set<UserRole> toRoleSet(List<UserRole> roles) {
    return roles == null || roles.isEmpty() ? Set.of(UserRole.USER) : new HashSet<>(roles);
  }

  /**
   * 부여 가능한 관리 역할: 기관 관리자는 항상, 인프라/자산 관리자는 <b>기관이 관련 기능을 켰을 때만</b> 노출한다
   * (기능 안 켠 역할은 아무 메뉴도 못 여니 제시하지 않음). 단, 대상이 이미 가진 역할은 유지해 실수로 회수되지
   * 않게 한다(기능이 나중에 꺼져도 기존 부여는 편집 가능).
   */
  private List<UserRole> assignableRoles(Set<UserRole> current) {
    var features = tenantService.getById(tenantContext.currentTenantId()).getFeatures();
    boolean infra = features.contains(com.moara.moa.tenant.FeatureModule.ASSETS)
        || features.contains(com.moara.moa.tenant.FeatureModule.SERVER_ACCESS)
        || features.contains(com.moara.moa.tenant.FeatureModule.SOLUTIONS)
        || features.contains(com.moara.moa.tenant.FeatureModule.CREDENTIALS);
    boolean asset = features.contains(com.moara.moa.tenant.FeatureModule.INVENTORY)
        || features.contains(com.moara.moa.tenant.FeatureModule.RESERVATION);
    List<UserRole> roles = new java.util.ArrayList<>();
    roles.add(UserRole.TENANT_ADMIN);
    if (infra || current.contains(UserRole.INFRA_MANAGER)) {
      roles.add(UserRole.INFRA_MANAGER);
    }
    if (asset || current.contains(UserRole.ASSET_MANAGER)) {
      roles.add(UserRole.ASSET_MANAGER);
    }
    return roles;
  }

  /** 폼 재표시용 모델(상태 + 부여 가능 관리 역할 + 현재 역할 집합 + 편집 가능 여부). */
  private void addFormModel(Model model, Set<UserRole> currentRoles) {
    Set<UserRole> current = currentRoles == null ? Set.of() : currentRoles;
    model.addAttribute("statuses", UserStatus.values());
    model.addAttribute("assignableRoles", assignableRoles(current));
    model.addAttribute("currentRoles", current);
    model.addAttribute("isAdminRole",
        current.contains(UserRole.TENANT_ADMIN) || current.contains(UserRole.INFRA_MANAGER)
            || current.contains(UserRole.ASSET_MANAGER));
    model.addAttribute("roleEditable", !current.contains(UserRole.SYSTEM_ADMIN));
  }

  @PostMapping("/users/{id}/approve")
  public String approve(@PathVariable UUID id) {
    ManagedUser approved = userService.approve(tenantContext.currentTenantId(), id);
    audit("USER_APPROVE", id, approved.getUsername());
    return "redirect:/users";
  }

  @PostMapping("/users/{id}/disable")
  public String disable(@PathVariable UUID id) {
    userService.disable(tenantContext.currentTenantId(), id);
    audit("USER_DISABLE", id, null);
    return "redirect:/users";
  }

  /** 비활성(휴면) 사용자를 다시 활성(재직)으로. 접근 권한은 보존돼 있으므로 그대로 복원된다. */
  @PostMapping("/users/{id}/activate")
  public String activate(@PathVariable UUID id) {
    userService.activate(tenantContext.currentTenantId(), id);
    audit("USER_ACTIVATE", id, null);
    return "redirect:/users";
  }

  /**
   * 퇴사 처리(종료). 대상의 모든 접근(그룹·직접 권한·솔루션 배정)을 회수하고 상태를 OFFBOARDED로 전이한다.
   * 자기 자신은 퇴사 처리할 수 없다(계정 잠금 방지).
   */
  @PostMapping("/users/{id}/offboard")
  public String offboard(@PathVariable UUID id, RedirectAttributes redirect) {
    if (id.equals(tenantContext.currentUserId())) {
      redirect.addFlashAttribute("userError", "본인 계정은 퇴사 처리할 수 없습니다.");
      return "redirect:/users";
    }
    OffboardResult result;
    try {
      result = lifecycleService.offboard(tenantContext.currentTenantId(), id);
    } catch (OffboardFailedException failure) {
      // 부분 회수를 막기 위해 전체가 롤백됐다. 어느 모듈에서 막혔는지 알려야 복구를 시작할 수 있다.
      auditFailure("USER_OFFBOARD", id, "회수 실패: " + failure.getHandlerName());
      redirect.addFlashAttribute("userError",
          "퇴사 처리에 실패해 아무것도 변경되지 않았습니다(" + failure.getHandlerName()
              + " 단계에서 오류). 관리자에게 문의하세요.");
      return "redirect:/users";
    }
    String detail = result.outcomes().stream()
        .map(outcome -> outcome.label() + " " + outcome.count())
        .collect(java.util.stream.Collectors.joining(", "));
    audit("USER_OFFBOARD", id, detail);
    redirect.addFlashAttribute("userMessage",
        "퇴사 처리 완료 — 회수·정리 " + result.total() + "건 (" + detail + ").");
    return "redirect:/users";
  }

  /** 비밀번호를 입력했을 때만 확인값과 일치해야 한다(미입력=변경 안 함이면 확인 불필요). */
  private boolean passwordMatches(String password, String confirm) {
    if (password == null || password.isBlank()) {
      return true;
    }
    return password.equals(confirm);
  }

  /** 실패한 특권 행위도 기록한다(성공만 남기면 사고 분석이 불가능하다). */
  private void auditFailure(String action, UUID targetId, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "ManagedUser", targetId,
          AuditResult.FAILURE, message);
    }
  }

  private void audit(String action, UUID targetId, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "ManagedUser", targetId, AuditResult.SUCCESS, message);
    }
  }
}
