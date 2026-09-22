package com.moara.moa.web;

import com.moara.moa.asset.AssetService;
import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.security.TenantContext;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.DuplicateManagedUserException;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.OperatorForm;
import com.moara.moa.user.UserStatus;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 플랫폼(SYSTEM_ADMIN) 콘솔. 개요/운영자 계정/플랫폼 감사/시스템 상태.
 * 접근 제어는 SecurityConfig({@code /admin/**})가 SYSTEM_ADMIN으로 강제한다.
 * 기관(테넌시) 관리(/admin/tenants)는 {@link TenantController}가 담당한다.
 */
@Controller
@RequestMapping("/admin")
public class PlatformController {
  private final TenantService tenantService;
  private final ManagedUserService userService;
  private final AssetService assetService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;
  private final JdbcTemplate jdbcTemplate;

  public PlatformController(
      TenantService tenantService,
      ManagedUserService userService,
      AssetService assetService,
      AuditLogService auditLogService,
      TenantContext tenantContext,
      JdbcTemplate jdbcTemplate) {
    this.tenantService = tenantService;
    this.userService = userService;
    this.assetService = assetService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
    this.jdbcTemplate = jdbcTemplate;
  }

  @GetMapping({"", "/"})
  public String overview(Model model) {
    LocalDate today = LocalDate.now();
    List<Tenant> tenants = tenantService.findAll();
    long expiring = tenants.stream().filter(t -> t.isExpiringSoon(today)).count();
    long expired = tenants.stream().filter(t -> t.isExpired(today)).count();

    List<ManagedUser> allUsers = userService.findAll();
    long activeUsers = allUsers.stream().filter(u -> u.getStatus() == UserStatus.ACTIVE).count();
    long pendingUsers = allUsers.stream().filter(u -> u.getStatus() == UserStatus.PENDING).count();

    // 지표: 기관(활성/만기임박/만료), 사용자(활성/승인대기), 운영자. (자산 수는 플랫폼 관심사가 아님 — 제거)
    model.addAttribute("tenantTotal", tenants.size());
    model.addAttribute("tenantActive", tenantService.countActive());
    model.addAttribute("tenantExpiring", expiring);
    model.addAttribute("tenantExpired", expired);
    model.addAttribute("userTotal", allUsers.size());
    model.addAttribute("activeUsers", activeUsers);
    model.addAttribute("pendingUsers", pendingUsers);
    model.addAttribute("operatorTotal", userService.findOperators().size());
    model.addAttribute("recentTenants", tenants);
    model.addAttribute("today", today);
    return "admin/overview";
  }

  @GetMapping("/operators")
  public String operators(Model model) {
    model.addAttribute("operators", userService.findOperators());
    if (!model.containsAttribute("operatorForm")) {
      model.addAttribute("operatorForm", new OperatorForm("", "", "", "", ""));
    }
    return "admin/operators";
  }

  @PostMapping("/operators")
  public String createOperator(
      @Valid @ModelAttribute("operatorForm") OperatorForm operatorForm,
      BindingResult bindingResult,
      @RequestParam(required = false) String passwordConfirm,
      Model model) {
    if (operatorForm.password() != null && !operatorForm.password().equals(passwordConfirm)) {
      bindingResult.rejectValue("password", "password.mismatch", "비밀번호가 일치하지 않습니다.");
    }
    if (!bindingResult.hasErrors()) {
      try {
        ManagedUser created = userService.createOperator(
            operatorForm.username(), operatorForm.name(), operatorForm.email(),
            operatorForm.phone(), operatorForm.password());
        audit("OPERATOR_CREATE", created.getId(), created.getUsername());
        return "redirect:/admin/operators";
      } catch (DuplicateManagedUserException exception) {
        bindingResult.rejectValue("username", "operator.duplicate", "이미 사용 중인 운영자 아이디입니다.");
      }
    }
    model.addAttribute("operators", userService.findOperators());
    return "admin/operators";
  }

  @PostMapping("/operators/{id}/disable")
  public String disableOperator(@PathVariable UUID id, Model model) {
    UUID actor = tenantContext.currentUserId();
    if (id.equals(actor)) {
      model.addAttribute("operators", userService.findOperators());
      model.addAttribute("operatorForm", new OperatorForm("", "", "", "", ""));
      model.addAttribute("selfDisableError", "본인 계정은 비활성화할 수 없습니다.");
      return "admin/operators";
    }
    userService.disable(id);
    audit("OPERATOR_DISABLE", id, null);
    return "redirect:/admin/operators";
  }

  @GetMapping("/audit")
  public String audit(Model model) {
    model.addAttribute("logs", auditLogService.findGlobal());
    return "admin/audit";
  }

  @GetMapping("/system")
  public String system(Model model) {
    model.addAttribute("schemaVersion", schemaVersion());
    model.addAttribute("dbOk", dbReachable());
    model.addAttribute("tenantTotal", tenantService.countAll());
    model.addAttribute("userTotal", userService.countUsers());
    model.addAttribute("assetTotal", assetService.countAll());
    return "admin/system";
  }

  private String schemaVersion() {
    try {
      String v = jdbcTemplate.queryForObject(
          "SELECT version FROM flyway_schema_history WHERE success = true "
              + "ORDER BY installed_rank DESC LIMIT 1",
          String.class);
      return v == null ? "(unknown)" : "V" + v;
    } catch (DataAccessException exception) {
      return "(unknown)";
    }
  }

  private boolean dbReachable() {
    try {
      jdbcTemplate.queryForObject("SELECT 1", Integer.class);
      return true;
    } catch (DataAccessException exception) {
      return false;
    }
  }

  private void audit(String action, UUID targetId, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordGlobalAction(
          actorId, null, action, "Operator", targetId, AuditResult.SUCCESS, message);
    }
  }
}
